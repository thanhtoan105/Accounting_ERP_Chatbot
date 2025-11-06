#!/usr/bin/env python3
"""
Import Chart of Accounts from CSV file.
Replaces existing chart of accounts data for company_id=1 with data from CSV.

Usage:
    python import_chart_of_accounts.py <csv_file_path>
"""

import csv
import sys
import os
import psycopg2
from typing import Dict, List, Optional, Tuple
from collections import defaultdict


# Database configuration
DB_CONFIG = {
    'host': os.getenv('DB_HOST', 'localhost'),
    'port': os.getenv('DB_PORT', '5432'),
    'database': os.getenv('DB_NAME', 'accounting_dev'),
    'user': os.getenv('DB_USER', 'accounting'),
    'password': os.getenv('DB_PASSWORD', 'accounting'),
    'schema': 'accounting'
}

COMPANY_ID = 1


def get_account_type(code: str) -> str:
    """
    Determine account type from first digit of account code.
    
    Rules:
    - 1, 2 → Asset
    - 3 → Liability
    - 4 → Equity
    - 5, 7, 9 → Revenue
    - 6, 8 → Expense
    """
    if not code or not code[0].isdigit():
        raise ValueError(f"Invalid account code: {code}")
    
    first_digit = int(code[0])
    
    if first_digit in [1, 2]:
        return 'Asset'
    elif first_digit == 3:
        return 'Liability'
    elif first_digit == 4:
        return 'Equity'
    elif first_digit in [5, 7, 9]:
        return 'Revenue'
    elif first_digit in [6, 8]:
        return 'Expense'
    else:
        raise ValueError(f"Unknown account type for code starting with {first_digit}: {code}")


def get_normal_side(tinh_chat: str, account_type: str) -> str:
    """
    Map Tính chất (nature) to normal_side.
    
    Rules:
    - "Dư Nợ" → "Debit"
    - "Dư Có" → "Credit"
    - "Lưỡng tính" → "Hermaphrodite"
    """
    if tinh_chat == 'Dư Nợ':
        return 'Debit'
    elif tinh_chat == 'Dư Có':
        return 'Credit'
    elif tinh_chat == 'Lưỡng tính':
        return 'Hermaphrodite'
    else:
        raise ValueError(f"Unknown Tính chất: {tinh_chat}")


def find_parent_code(code: str, all_codes: set) -> Optional[str]:
    """
    Find parent account code based on hierarchy rules.
    
    Rules from plan:
    - 3-digit codes → parent_id = NULL (top-level)
    - 4-digit codes → parent is 3-digit code
    - 5-digit codes → parent is 4-digit code
    
    Only returns parent code if it exists in the CSV.
    """
    code_length = len(code)
    
    if code_length <= 3:
        return None  # Top-level account
    elif code_length == 4:
        # Parent is 3-digit code
        parent_code = code[:3]
        return parent_code if parent_code in all_codes else None
    elif code_length == 5:
        # Parent is 4-digit code
        parent_code = code[:4]
        return parent_code if parent_code in all_codes else None
    else:
        # For codes longer than 5 digits, try 4-digit parent first, then 3-digit
        if code_length > 5:
            parent_4 = code[:4]
            if parent_4 in all_codes:
                return parent_4
            parent_3 = code[:3]
            if parent_3 in all_codes:
                return parent_3
        return None


def parse_csv(csv_file_path: str) -> List[Dict]:
    """
    Parse CSV file and extract account information.
    
    CSV format:
    STT;Số tài khoản;Tên tài khoản;Tính chất;Tên tiếng Anh;Trạng thái
    """
    accounts = []
    
    with open(csv_file_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f, delimiter=';')
        
        for row in reader:
            # utf-8-sig encoding handles BOM automatically
            code = row['Số tài khoản'].strip()
            name = row['Tên tài khoản'].strip()
            tinh_chat = row['Tính chất'].strip()
            
            if not code or not name:
                print(f"Warning: Skipping row with missing code or name: {row}")
                continue
            
            # Determine account type
            account_type = get_account_type(code)
            
            # Determine normal side
            normal_side = get_normal_side(tinh_chat, account_type)
            
            # Find parent code (will be resolved later after all codes are known)
            parent_code = None  # Will be set in build_hierarchy
            
            # Ordering position = code as integer
            try:
                ordering_position = int(code)
            except ValueError:
                # If code is not a pure integer, use a hash or fallback
                ordering_position = hash(code) % 1000000
            
            accounts.append({
                'code': code,
                'name': name,
                'type': account_type,
                'normal_side': normal_side,
                'parent_code': parent_code,
                'ordering_position': ordering_position,
            })
    
    return accounts


def build_hierarchy(accounts: List[Dict]) -> Tuple[Dict[str, Dict], Dict[str, List[str]]]:
    """
    Build account hierarchy and determine which accounts are leaf nodes.
    
    Returns:
        - accounts_dict: code -> account_data (with parent_code set)
        - children_map: code -> list of child codes
    """
    # Create set of all codes for parent lookup
    all_codes = {acc['code'] for acc in accounts}
    
    # Create code to account mapping and resolve parent codes
    accounts_dict = {}
    for acc in accounts:
        acc_copy = acc.copy()
        # Find parent code based on hierarchy rules
        parent_code = find_parent_code(acc['code'], all_codes)
        acc_copy['parent_code'] = parent_code
        accounts_dict[acc['code']] = acc_copy
    
    # Build children map
    children_map = defaultdict(list)
    for code, acc in accounts_dict.items():
        if acc['parent_code']:
            if acc['parent_code'] in accounts_dict:
                children_map[acc['parent_code']].append(code)
            else:
                # Parent not in CSV, treat as top-level
                acc['parent_code'] = None
    
    return accounts_dict, children_map


def determine_postable(accounts_dict: Dict[str, Dict], children_map: Dict[str, List[str]]) -> None:
    """
    Set postable flag: only leaf accounts (no children) are postable.
    """
    for code in accounts_dict:
        has_children = code in children_map and len(children_map[code]) > 0
        accounts_dict[code]['postable'] = not has_children


def insert_accounts(conn, accounts_dict: Dict[str, Dict], children_map: Dict[str, List[str]]) -> None:
    """
    Delete existing accounts and insert new ones.
    Insert in order: parents before children.
    """
    cursor = conn.cursor()
    
    try:
        # 1) Break FKs before deleting accounts
        print("Nulling voucher_types debit/credit account references...")
        cursor.execute(
            f"""
            UPDATE {DB_CONFIG['schema']}.voucher_types vt
            SET debit_account_id = NULL
            WHERE debit_account_id IN (
              SELECT id FROM {DB_CONFIG['schema']}.chart_of_accounts WHERE company_id = %s
            )
            """,
            (COMPANY_ID,)
        )
        cursor.execute(
            f"""
            UPDATE {DB_CONFIG['schema']}.voucher_types vt
            SET credit_account_id = NULL
            WHERE credit_account_id IN (
              SELECT id FROM {DB_CONFIG['schema']}.chart_of_accounts WHERE company_id = %s
            )
            """,
            (COMPANY_ID,)
        )

        print("Deleting voucher_lines that reference company accounts...")
        cursor.execute(
            f"""
            DELETE FROM {DB_CONFIG['schema']}.voucher_lines vl
            WHERE vl.account_id IN (
              SELECT id FROM {DB_CONFIG['schema']}.chart_of_accounts WHERE company_id = %s
            )
            """,
            (COMPANY_ID,)
        )

        # 2) Delete existing accounts for company_id=1
        print(f"Deleting existing accounts for company_id={COMPANY_ID}...")
        cursor.execute(
            f"DELETE FROM {DB_CONFIG['schema']}.chart_of_accounts WHERE company_id = %s",
            (COMPANY_ID,)
        )
        deleted_count = cursor.rowcount
        print(f"Deleted {deleted_count} existing accounts")
        
        # Sort accounts by code length and code value (parents before children)
        # This ensures parent accounts are inserted before their children
        sorted_accounts = sorted(accounts_dict.values(), key=lambda x: (len(x['code']), int(x['code']) if x['code'].isdigit() else 0))
        
        # Create code to id mapping as we insert
        code_to_id = {}
        
        # Insert accounts
        print(f"Inserting {len(sorted_accounts)} new accounts...")
        inserted_count = 0
        
        for acc in sorted_accounts:
            # Resolve parent_id
            parent_id = None
            if acc['parent_code'] and acc['parent_code'] in code_to_id:
                parent_id = code_to_id[acc['parent_code']]
            
            # Insert account
            cursor.execute(f"""
                INSERT INTO {DB_CONFIG['schema']}.chart_of_accounts 
                (company_id, code, name, type, normal_side, postable, parent_id, ordering_position)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                RETURNING id
            """, (
                COMPANY_ID,
                acc['code'],
                acc['name'],
                acc['type'],
                acc['normal_side'],
                acc['postable'],
                parent_id,
                acc['ordering_position']
            ))
            
            # Get the inserted id
            account_id = cursor.fetchone()[0]
            code_to_id[acc['code']] = account_id
            inserted_count += 1
            
            if inserted_count % 50 == 0:
                print(f"  Inserted {inserted_count}/{len(sorted_accounts)} accounts...")
        
        # Commit transaction
        conn.commit()
        print(f"\nSuccessfully inserted {inserted_count} accounts")
        
        # Print summary
        print("\nSummary:")
        print(f"  Total accounts: {inserted_count}")
        postable_count = sum(1 for acc in accounts_dict.values() if acc['postable'])
        print(f"  Postable accounts: {postable_count}")
        print(f"  Non-postable accounts: {inserted_count - postable_count}")
        
        # Count by type
        type_counts = defaultdict(int)
        for acc in accounts_dict.values():
            type_counts[acc['type']] += 1
        print("\nAccounts by type:")
        for acc_type, count in sorted(type_counts.items()):
            print(f"  {acc_type}: {count}")
        
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        cursor.close()


def main():
    if len(sys.argv) != 2:
        print("Usage: python import_chart_of_accounts.py <csv_file_path>")
        sys.exit(1)
    
    csv_file_path = sys.argv[1]
    
    if not os.path.exists(csv_file_path):
        print(f"Error: CSV file not found: {csv_file_path}")
        sys.exit(1)
    
    print("=" * 60)
    print("Chart of Accounts CSV Import Script")
    print("=" * 60)
    print(f"CSV file: {csv_file_path}")
    print(f"Company ID: {COMPANY_ID}")
    print(f"Database: {DB_CONFIG['database']}@{DB_CONFIG['host']}:{DB_CONFIG['port']}")
    print("=" * 60)
    print()
    
    try:
        # Parse CSV
        print("Step 1: Parsing CSV file...")
        accounts = parse_csv(csv_file_path)
        print(f"  Parsed {len(accounts)} accounts from CSV")
        
        # Build hierarchy
        print("\nStep 2: Building account hierarchy...")
        accounts_dict, children_map = build_hierarchy(accounts)
        print(f"  Built hierarchy with {len(accounts_dict)} accounts")
        
        # Determine postable flag
        print("\nStep 3: Determining postable flags...")
        determine_postable(accounts_dict, children_map)
        postable_count = sum(1 for acc in accounts_dict.values() if acc['postable'])
        print(f"  {postable_count} accounts are postable (leaf nodes)")
        
        # Connect to database
        print("\nStep 4: Connecting to database...")
        conn = psycopg2.connect(
            host=DB_CONFIG['host'],
            port=DB_CONFIG['port'],
            database=DB_CONFIG['database'],
            user=DB_CONFIG['user'],
            password=DB_CONFIG['password']
        )
        print("  Connected successfully")
        
        # Insert accounts
        print("\nStep 5: Importing accounts to database...")
        insert_accounts(conn, accounts_dict, children_map)
        
        # Close connection
        conn.close()
        print("\n" + "=" * 60)
        print("Import completed successfully!")
        print("=" * 60)
        
    except Exception as e:
        print(f"\nError: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()

