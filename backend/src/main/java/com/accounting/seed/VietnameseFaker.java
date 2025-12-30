package com.accounting.seed;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import net.datafaker.Faker;

public class VietnameseFaker {

    private static final List<String> COMPANY_PREFIXES = List.of(
            "Công ty TNHH", "Công ty Cổ phần", "Công ty TNHH MTV",
            "Công ty TNHH TM-DV", "Công ty TNHH SX-TM"
    );

    private static final List<String> COMPANY_NAMES = List.of(
            "Thành Công", "Phát Đạt", "Minh Quang", "Hoàng Long", "Việt Tiến",
            "An Phú", "Đại Phát", "Hưng Thịnh", "Tân Phát", "Phú Thịnh",
            "Bình Minh", "Thiên Long", "Trường Phát", "Kim Cương", "Bảo An",
            "Nhật Minh", "Đông Á", "Tây Đô", "Nam Việt", "Bắc Hà"
    );

    private static final List<String> FIRST_NAMES = List.of(
            "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ",
            "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"
    );

    private static final List<String> MIDDLE_NAMES = List.of(
            "Văn", "Thị", "Hữu", "Đức", "Minh", "Quang", "Thanh", "Hoàng",
            "Xuân", "Thu", "Ngọc", "Kim", "Thành", "Phúc", "Bảo", "Anh"
    );

    private static final List<String> GIVEN_NAMES = List.of(
            "An", "Bình", "Chi", "Dũng", "Em", "Hà", "Hải", "Hùng", "Lan",
            "Linh", "Long", "Mai", "Minh", "Nam", "Phong", "Quang", "Sơn",
            "Tâm", "Thảo", "Tú", "Tuấn", "Vân", "Việt", "Yến", "Hương"
    );

    private static final List<String> STREETS = List.of(
            "Lê Lợi", "Nguyễn Huệ", "Trần Hưng Đạo", "Lý Thường Kiệt", "Hai Bà Trưng",
            "Điện Biên Phủ", "Võ Văn Tần", "Nguyễn Đình Chiểu", "Cách Mạng Tháng 8",
            "3 Tháng 2", "Nguyễn Trãi", "Lê Văn Sỹ", "Phan Đình Phùng", "Hoàng Văn Thụ"
    );

    private static final List<String> DISTRICTS_HCM = List.of(
            "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 7", "Quận 10",
            "Quận Bình Thạnh", "Quận Phú Nhuận", "Quận Tân Bình", "Quận Gò Vấp"
    );

    private static final List<String> DISTRICTS_HN = List.of(
            "Quận Ba Đình", "Quận Hoàn Kiếm", "Quận Đống Đa", "Quận Hai Bà Trưng",
            "Quận Cầu Giấy", "Quận Thanh Xuân", "Quận Long Biên", "Quận Nam Từ Liêm"
    );

    private static final List<String> CITIES = List.of(
            "TP. Hồ Chí Minh", "Hà Nội", "Đà Nẵng", "Hải Phòng", "Cần Thơ",
            "Biên Hòa", "Nha Trang", "Huế", "Buôn Ma Thuột", "Quy Nhơn"
    );

    private static final List<String> BANK_NAMES = List.of(
            "Vietcombank", "BIDV", "Agribank", "Techcombank", "VPBank",
            "MB Bank", "ACB", "Sacombank", "VietinBank", "SHB",
            "TPBank", "HDBank", "OCB", "MSB", "SeABank"
    );

    private static final List<String> PRODUCT_DESCRIPTIONS = List.of(
            "Vật tư văn phòng", "Thiết bị điện tử", "Nguyên vật liệu sản xuất",
            "Dịch vụ vận chuyển", "Dịch vụ tư vấn", "Phần mềm quản lý",
            "Máy móc thiết bị", "Hàng hóa nhập khẩu", "Sản phẩm nông nghiệp",
            "Dịch vụ bảo trì", "Vật liệu xây dựng", "Thiết bị y tế",
            "Linh kiện điện tử", "Dịch vụ marketing", "Thiết bị công nghiệp"
    );

    private final Faker faker;
    private int companyNameCounter = 0;

    public VietnameseFaker(long seed) {
        this.faker = new Faker(new Locale("vi"), new java.util.Random(seed));
    }

    public String companyName() {
        String prefix = COMPANY_PREFIXES.get(faker.random().nextInt(COMPANY_PREFIXES.size()));
        String name = COMPANY_NAMES.get(faker.random().nextInt(COMPANY_NAMES.size()));
        companyNameCounter++;
        return prefix + " " + name + " " + companyNameCounter;
    }

    public String uniqueCompanyName(String suffix) {
        String prefix = COMPANY_PREFIXES.get(faker.random().nextInt(COMPANY_PREFIXES.size()));
        String name = COMPANY_NAMES.get(faker.random().nextInt(COMPANY_NAMES.size()));
        return prefix + " " + name + " " + suffix;
    }

    public String personName() {
        String firstName = FIRST_NAMES.get(faker.random().nextInt(FIRST_NAMES.size()));
        String middleName = MIDDLE_NAMES.get(faker.random().nextInt(MIDDLE_NAMES.size()));
        String givenName = GIVEN_NAMES.get(faker.random().nextInt(GIVEN_NAMES.size()));
        return firstName + " " + middleName + " " + givenName;
    }

    public String address() {
        int streetNumber = faker.random().nextInt(1, 500);
        String street = STREETS.get(faker.random().nextInt(STREETS.size()));
        String city = CITIES.get(faker.random().nextInt(CITIES.size()));

        String district;
        if (city.contains("Hồ Chí Minh")) {
            district = DISTRICTS_HCM.get(faker.random().nextInt(DISTRICTS_HCM.size()));
        } else if (city.contains("Hà Nội")) {
            district = DISTRICTS_HN.get(faker.random().nextInt(DISTRICTS_HN.size()));
        } else {
            district = "Quận " + faker.random().nextInt(1, 5);
        }

        return streetNumber + " Đường " + street + ", " + district + ", " + city;
    }

    public String taxCode() {
        StringBuilder sb = new StringBuilder();
        sb.append(faker.random().nextInt(1, 9));
        for (int i = 0; i < 9; i++) {
            sb.append(faker.random().nextInt(0, 10));
        }
        return sb.toString();
    }

    public String phoneNumber() {
        String[] prefixes = {"09", "03", "07", "08", "05"};
        String prefix = prefixes[faker.random().nextInt(prefixes.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 8; i++) {
            sb.append(faker.random().nextInt(0, 10));
        }
        return sb.toString();
    }

    public String email(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("đ", "d")
                .replaceAll("Đ", "D")
                .toLowerCase()
                .replaceAll("\\s+", ".");

        String[] domains = {"gmail.com", "yahoo.com.vn", "outlook.com", "company.com.vn"};
        String domain = domains[faker.random().nextInt(domains.length)];

        return normalized + faker.random().nextInt(1, 100) + "@" + domain;
    }

    public String bankName() {
        return BANK_NAMES.get(faker.random().nextInt(BANK_NAMES.size()));
    }

    public String accountNumber() {
        StringBuilder sb = new StringBuilder();
        int length = faker.random().nextInt(10, 15);
        for (int i = 0; i < length; i++) {
            sb.append(faker.random().nextInt(0, 10));
        }
        return sb.toString();
    }

    public BigDecimal vndAmount(long min, long max) {
        long minThousands = min / 1000;
        long maxThousands = max / 1000;
        long thousands = faker.random().nextLong(minThousands, maxThousands + 1);
        return BigDecimal.valueOf(thousands * 1000);
    }

    public String productDescription() {
        return PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size()));
    }

    // ==================== DETAILED DESCRIPTION GENERATORS ====================

    private static final List<String> PRODUCT_CATEGORIES = List.of(
            "Máy tính xách tay", "Máy in laser", "Điều hòa không khí", "Tủ lạnh công nghiệp",
            "Máy photocopy", "Màn hình LCD", "Bàn làm việc", "Ghế văn phòng",
            "Máy chiếu", "Camera an ninh", "Hệ thống mạng", "Server", "UPS",
            "Máy quét mã vạch", "Thiết bị POS", "Xe nâng điện", "Máy đóng gói",
            "Dây chuyền sản xuất", "Máy CNC", "Robot công nghiệp"
    );

    private static final List<String> MATERIALS = List.of(
            "Thép cuộn cán nóng", "Nhôm tấm 2mm", "Ống inox 304", "Tôn mạ kẽm",
            "Gỗ MDF chống ẩm", "Kính cường lực 10mm", "Nhựa PP nguyên sinh",
            "Vải cotton 100%", "Da PU cao cấp", "Sợi carbon", "Cao su tổng hợp",
            "Xi măng PCB40", "Cát xây dựng", "Đá granite", "Gạch ceramic"
    );

    private static final List<String> SERVICES = List.of(
            "Bảo trì hệ thống CNTT", "Vệ sinh công nghiệp", "Bảo vệ 24/7",
            "Vận chuyển hàng hóa", "Lắp đặt thiết bị", "Đào tạo nhân viên",
            "Tư vấn ISO 9001", "Kiểm toán nội bộ", "Marketing số",
            "Thiết kế website", "Phát triển phần mềm", "Cho thuê xe du lịch",
            "Catering sự kiện", "Dịch vụ kế toán", "Tư vấn pháp lý"
    );

    private static final List<String> UNITS = List.of(
            "cái", "bộ", "hộp", "thùng", "kg", "tấn", "m", "m²", "m³", "lít", "cuộn", "tấm"
    );

    private static final List<String> CONTRACT_PREFIXES = List.of(
            "HĐ", "HĐKT", "HĐMB", "HĐDV", "PO"
    );

    /**
     * Generate detailed purchase bill description with context.
     * Examples:
     * - "Mua 50 thùng Giấy A4 Double A theo HĐ-2024-0156"
     * - "Thanh toán đợt 2 hợp đồng HĐKT-2024-089 - Máy photocopy Ricoh"
     */
    public String purchaseBillDescription(String supplierName, int year) {
        int type = faker.random().nextInt(5);
        String contractNum = CONTRACT_PREFIXES.get(faker.random().nextInt(CONTRACT_PREFIXES.size()))
                + "-" + year + "-" + String.format("%04d", faker.random().nextInt(1, 9999));

        return switch (type) {
            case 0 -> {
                String product = PRODUCT_CATEGORIES.get(faker.random().nextInt(PRODUCT_CATEGORIES.size()));
                int qty = faker.random().nextInt(1, 20);
                yield String.format("Mua %d %s %s theo %s",
                        qty, UNITS.get(faker.random().nextInt(4)), product, contractNum);
            }
            case 1 -> {
                String material = MATERIALS.get(faker.random().nextInt(MATERIALS.size()));
                int qty = faker.random().nextInt(10, 500);
                String unit = UNITS.get(faker.random().nextInt(UNITS.size()));
                yield String.format("Nhập %d %s %s từ %s",
                        qty, unit, material, shortenName(supplierName));
            }
            case 2 -> {
                String service = SERVICES.get(faker.random().nextInt(SERVICES.size()));
                String month = String.format("T%02d/%d", faker.random().nextInt(1, 13), year);
                yield String.format("%s %s - %s", service, month, shortenName(supplierName));
            }
            case 3 -> {
                int installment = faker.random().nextInt(1, 5);
                String product = PRODUCT_CATEGORIES.get(faker.random().nextInt(PRODUCT_CATEGORIES.size()));
                yield String.format("Thanh toán đợt %d/%d - %s theo %s",
                        installment, installment + faker.random().nextInt(1, 3), product, contractNum);
            }
            default -> {
                String product = PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size()));
                yield String.format("Mua %s - %s - %s", product, shortenName(supplierName), contractNum);
            }
        };
    }

    /**
     * Generate detailed sales invoice description.
     * Examples:
     * - "Bán 100 bộ Phần mềm quản lý kho cho Công ty ABC"
     * - "Cung cấp dịch vụ bảo trì CNTT tháng 12/2024"
     */
    public String salesInvoiceDescription(String customerName, int year) {
        int type = faker.random().nextInt(5);
        String contractNum = CONTRACT_PREFIXES.get(faker.random().nextInt(CONTRACT_PREFIXES.size()))
                + "-" + year + "-" + String.format("%04d", faker.random().nextInt(1, 9999));

        return switch (type) {
            case 0 -> {
                String product = PRODUCT_CATEGORIES.get(faker.random().nextInt(PRODUCT_CATEGORIES.size()));
                int qty = faker.random().nextInt(1, 50);
                yield String.format("Bán %d %s %s cho %s",
                        qty, UNITS.get(faker.random().nextInt(4)), product, shortenName(customerName));
            }
            case 1 -> {
                String service = SERVICES.get(faker.random().nextInt(SERVICES.size()));
                String month = String.format("T%02d/%d", faker.random().nextInt(1, 13), year);
                yield String.format("Cung cấp %s %s - %s", service, month, shortenName(customerName));
            }
            case 2 -> {
                String product = PRODUCT_CATEGORIES.get(faker.random().nextInt(PRODUCT_CATEGORIES.size()));
                yield String.format("Xuất hóa đơn %s theo %s - %s",
                        product, contractNum, shortenName(customerName));
            }
            case 3 -> {
                String service = SERVICES.get(faker.random().nextInt(SERVICES.size()));
                int installment = faker.random().nextInt(1, 4);
                yield String.format("Thu phí %s đợt %d - %s", service, installment, shortenName(customerName));
            }
            default -> {
                String product = PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size()));
                yield String.format("Bán hàng - %s - %s - %s", product, shortenName(customerName), contractNum);
            }
        };
    }

    /**
     * Generate detailed voucher description based on type.
     */
    public String voucherDescription(String voucherType, String partyName, int year) {
        String contractNum = CONTRACT_PREFIXES.get(faker.random().nextInt(CONTRACT_PREFIXES.size()))
                + "-" + year + "-" + String.format("%04d", faker.random().nextInt(1, 9999));

        return switch (voucherType) {
            case "PT" -> { // Cash Receipt
                int variant = faker.random().nextInt(4);
                yield switch (variant) {
                    case 0 -> String.format("Thu tiền mặt từ %s - %s", shortenName(partyName), contractNum);
                    case 1 -> String.format("Thu công nợ KH %s - Đợt %d", shortenName(partyName), faker.random().nextInt(1, 5));
                    case 2 -> String.format("Thu tiền bán hàng - %s", shortenName(partyName));
                    default -> String.format("Thu tiền theo HĐ %s - %s", contractNum, shortenName(partyName));
                };
            }
            case "PC" -> { // Cash Payment
                int variant = faker.random().nextInt(4);
                yield switch (variant) {
                    case 0 -> String.format("Chi tiền mặt cho %s - %s", shortenName(partyName), contractNum);
                    case 1 -> String.format("Thanh toán NCC %s - Đợt %d", shortenName(partyName), faker.random().nextInt(1, 5));
                    case 2 -> {
                        String expense = List.of("tiền điện", "tiền nước", "tiền internet", "văn phòng phẩm",
                                "tiếp khách", "xăng xe", "bảo hiểm", "thuế").get(faker.random().nextInt(8));
                        yield String.format("Chi %s T%02d/%d", expense, faker.random().nextInt(1, 13), year);
                    }
                    default -> String.format("Chi mua %s", PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size())));
                };
            }
            case "BC" -> { // Bank Receipt
                int variant = faker.random().nextInt(3);
                yield switch (variant) {
                    case 0 -> String.format("Báo có - Thu tiền từ %s theo %s", shortenName(partyName), contractNum);
                    case 1 -> String.format("Chuyển khoản đến từ %s - Thu công nợ", shortenName(partyName));
                    default -> String.format("Nhận thanh toán HĐ %s - %s", contractNum, shortenName(partyName));
                };
            }
            case "UNC" -> { // Bank Payment
                int variant = faker.random().nextInt(3);
                yield switch (variant) {
                    case 0 -> String.format("UNC thanh toán %s theo %s", shortenName(partyName), contractNum);
                    case 1 -> String.format("Chuyển khoản trả NCC %s - Đợt %d", shortenName(partyName), faker.random().nextInt(1, 5));
                    default -> String.format("Thanh toán HĐ %s - %s", contractNum, shortenName(partyName));
                };
            }
            case "HD" -> { // Sales Posting
                yield String.format("Hạch toán doanh thu - %s - %s",
                        PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size())),
                        shortenName(partyName));
            }
            case "NK" -> { // Purchase Posting
                yield String.format("Hạch toán mua hàng - %s - %s",
                        MATERIALS.get(faker.random().nextInt(MATERIALS.size())),
                        shortenName(partyName));
            }
            case "JV" -> { // General Journal
                int variant = faker.random().nextInt(5);
                yield switch (variant) {
                    case 0 -> String.format("Phân bổ chi phí T%02d/%d", faker.random().nextInt(1, 13), year);
                    case 1 -> String.format("Kết chuyển cuối kỳ T%02d/%d", faker.random().nextInt(1, 13), year);
                    case 2 -> String.format("Trích khấu hao TSCĐ T%02d/%d", faker.random().nextInt(1, 13), year);
                    case 3 -> "Điều chỉnh chênh lệch tỷ giá";
                    default -> String.format("Bút toán điều chỉnh - %s", PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size())));
                };
            }
            default -> "Ghi sổ nghiệp vụ kế toán";
        };
    }

    /**
     * Generate detailed product/service line description for invoices.
     */
    public String lineItemDescription() {
        int type = faker.random().nextInt(4);
        return switch (type) {
            case 0 -> {
                String product = PRODUCT_CATEGORIES.get(faker.random().nextInt(PRODUCT_CATEGORIES.size()));
                String spec = List.of("model A1", "phiên bản Pro", "loại cao cấp", "size L",
                        "màu đen", "nhập khẩu", "chính hãng").get(faker.random().nextInt(7));
                yield product + " - " + spec;
            }
            case 1 -> {
                String material = MATERIALS.get(faker.random().nextInt(MATERIALS.size()));
                String grade = List.of("loại 1", "hạng A", "tiêu chuẩn ISO", "xuất khẩu")
                        .get(faker.random().nextInt(4));
                yield material + " " + grade;
            }
            case 2 -> {
                String service = SERVICES.get(faker.random().nextInt(SERVICES.size()));
                String period = List.of("tháng " + faker.random().nextInt(1, 13),
                        "quý " + faker.random().nextInt(1, 5),
                        "năm " + (2024 + faker.random().nextInt(0, 2))).get(faker.random().nextInt(3));
                yield service + " " + period;
            }
            default -> PRODUCT_DESCRIPTIONS.get(faker.random().nextInt(PRODUCT_DESCRIPTIONS.size()));
        };
    }

    private String shortenName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "Đối tác";
        }
        // Extract key part: "Công ty TNHH ABC 123" -> "ABC 123"
        String[] parts = fullName.split(" ");
        if (parts.length > 3) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, parts.length - 2, parts.length));
        }
        return fullName.length() > 30 ? fullName.substring(0, 27) + "..." : fullName;
    }

    public Faker getFaker() {
        return faker;
    }
}
