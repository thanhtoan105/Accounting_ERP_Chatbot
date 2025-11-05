"use client";
import { useState, forwardRef, useEffect } from "react";
import parsePhoneNumber, { isValidPhoneNumber } from "libphonenumber-js";
import { CircleFlag } from "react-circle-flags";
import { lookup } from "country-data-list";
import { z } from "zod";
import { cn } from "@/lib/utils";

import { GlobeIcon, ChevronDown } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Input as UiInput } from '@/components/ui/input'

export const phoneSchema = z.string().refine((value) => {
    try {
        return isValidPhoneNumber(value);
    } catch {
        return false;
    }
}, "Invalid phone number");

export type CountryData = {
    alpha2: string;
    alpha3: string;
    countryCallingCodes: string[];
    currencies: string[];
    emoji?: string;
    ioc: string;
    languages: string[];
    name: string;
    status: string;
};

interface PhoneInputProps
    extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange"> {
    onCountryChange?: (data: CountryData | undefined) => void;
    value?: string;
    onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
    placeholder?: string;
    defaultCountry?: string;
    className?: string;
    inline?: boolean;
}

export const PhoneInput = forwardRef<HTMLInputElement, PhoneInputProps>(
    (
        {
            className,
            onCountryChange,
            onChange,
            value,
            placeholder,
            defaultCountry,
            inline = false,
            ...props
        },
        ref
    ) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const [countryData, setCountryData] = useState<CountryData | undefined>();
        const [displayFlag, setDisplayFlag] = useState<string>("");
        const [hasInitialized, setHasInitialized] = useState(false);
        const [countryOpen, setCountryOpen] = useState(false);
        const [query, setQuery] = useState("");

        // Utils to read countries from country-data-list (supports both function and object forms)
        function getAllCountries(): CountryData[] {
            const source: any = (lookup as any).countries;
            try {
                if (typeof source === 'function') {
                    // some builds expose a function that accepts a filter object
                    const result = source({});
                    return Array.isArray(result) ? (result as CountryData[]) : [];
                }
                if (source && typeof source === 'object') {
                    return Object.values(source) as CountryData[];
                }
            } catch {
                // ignore
            }
            return [];
        }

        function getByAlpha2(alpha2?: string): CountryData | undefined {
            if (!alpha2) return undefined;
            const source: any = (lookup as any).countries;
            try {
                if (typeof source === 'function') {
                    const result = source({ alpha2 });
                    if (Array.isArray(result)) return result[0] as CountryData | undefined;
                }
                const all = getAllCountries();
                return all.find((c) => (c.alpha2 || '').toLowerCase() === alpha2.toLowerCase());
            } catch {
                return undefined;
            }
        }

        const countries = getAllCountries();
        const filtered = query
            ? countries.filter((c: CountryData) => (c.name || '').toLowerCase().includes(query.toLowerCase()))
            : countries;

        // Initialize from defaultCountry ONLY when there is no current value
        useEffect(() => {
            if (defaultCountry && !value) {
                const newCountryData = getByAlpha2(defaultCountry.toLowerCase());
                setCountryData(newCountryData);
                setDisplayFlag(defaultCountry.toLowerCase());

                if (!hasInitialized && newCountryData?.countryCallingCodes?.[0]) {
                    const syntheticEvent = {
                        target: {
                            value: newCountryData.countryCallingCodes[0],
                        },
                    } as React.ChangeEvent<HTMLInputElement>;
                    onChange?.(syntheticEvent);
                    setHasInitialized(true);
                }
            }
        }, [defaultCountry, onChange, value, hasInitialized]);

        // Keep flag/country in sync when value is controlled from outside
        useEffect(() => {
            if (!value) return;
            try {
                const parsed = parsePhoneNumber(value);
                if (parsed?.country) {
                    const cc = parsed.country.toLowerCase();
                    if (cc !== displayFlag) setDisplayFlag(cc);
                    const countryInfo = getByAlpha2(parsed.country);
                    setCountryData(countryInfo);
                    onCountryChange?.(countryInfo);
                }
            } catch {
                // ignore if not parseable yet
            }
        }, [value]);

        const handlePhoneChange = (e: React.ChangeEvent<HTMLInputElement>) => {
            let newValue = e.target.value;

            // Ensure the value starts with "+"
            if (!newValue.startsWith("+")) {
                // Replace "00" at the start with "+" if present
                if (newValue.startsWith("00")) {
                    newValue = "+" + newValue.slice(2);
                } else {
                    // Otherwise just add "+" at the start
                    newValue = "+" + newValue;
                }
            }

            try {
                const parsed = parsePhoneNumber(newValue);
                console.log("Phone number details:", {
                    isPossible: parsed?.isPossible(),
                    isValid: parsed?.isValid(),
                    country: parsed?.country,
                    nationalNumber: parsed?.nationalNumber,
                    formatNational: parsed?.formatNational(),
                    formatInternational: parsed?.formatInternational(),
                    getType: parsed?.getType(),
                    countryCallingCode: parsed?.countryCallingCode,
                    getURI: parsed?.getURI(),
                    parsed: parsed,
                });

                if (parsed && parsed.country) {
                    // Update flag first
                    const countryCode = parsed.country;
                    console.log("Setting flag to:", countryCode.toLowerCase());

                    // Force immediate update
                    setDisplayFlag(""); // Clear first
                    setTimeout(() => {
                        setDisplayFlag(countryCode.toLowerCase()); // Then set new value
                    }, 0);

                    // Update other state
                    const countryInfo = lookup.countries({ alpha2: countryCode })[0];
                    setCountryData(countryInfo);
                    onCountryChange?.(countryInfo);

                    // Update input value
                    const syntheticEvent = {
                        ...e,
                        target: {
                            ...e.target,
                            value: parsed.number,
                        },
                    } as React.ChangeEvent<HTMLInputElement>;
                    onChange?.(syntheticEvent);
                } else {
                    onChange?.(e);
                    setDisplayFlag("");
                    setCountryData(undefined);
                    onCountryChange?.(undefined);
                }
            } catch (error) {
                console.error("Error parsing phone number:", error);
                onChange?.(e);
                setDisplayFlag("");
                setCountryData(undefined);
                onCountryChange?.(undefined);
            }
        };

        const inputClasses = cn(
            "flex items-center gap-2 relative bg-transparent transition-colors text-base rounded-md border border-input pl-3 h-9 shadow-sm disabled:opacity-50 disabled:cursor-not-allowed md:text-sm has-[input:focus]:outline-none has-[input:focus]:ring-1 has-[input:focus]:ring-ring [interpolate-size:allow-keywords]",
            inline && "rounded-l-none w-full",
            className
        );

        return (
            <div className={inputClasses}>
                {!inline && (
                    <Popover open={countryOpen} onOpenChange={setCountryOpen}>
                        <PopoverTrigger asChild>
                            <button
                                type="button"
                                className="flex items-center gap-1 px-0.5 text-foreground"
                                aria-label="Choose country"
                            >
                                <div className="w-4 h-4 rounded-full shrink-0">
                                    {displayFlag ? (
                                        <CircleFlag countryCode={displayFlag} height={16} />
                                    ) : (
                                        <GlobeIcon size={16} />
                                    )}
                                </div>
                                <ChevronDown className="h-3 w-3" />
                            </button>
                        </PopoverTrigger>
                        <PopoverContent align="start" className="p-2 w-72">
                            <div className="grid gap-2">
                                <UiInput
                                    placeholder="Search country..."
                                    value={query}
                                    onChange={(e) => setQuery(e.target.value)}
                                />
                                <div className="max-h-64 overflow-auto">
                                    {filtered.map((c: CountryData) => (
                                        <button
                                            key={c.alpha2}
                                            type="button"
                                            className="w-full flex items-center gap-2 px-2 py-2 rounded-md hover:bg-accent text-left"
                                            onClick={() => {
                                                const cc = (c.alpha2 || '').toLowerCase();
                                                setDisplayFlag(cc);
                                                setCountryData(c as any);
                                                onCountryChange?.(c as any);
                                                const dial = c.countryCallingCodes?.[0] || '';
                                                // Replace current prefix or set new code
                                                const next = dial || '';
                                                const syntheticEvent = {
                                                    target: { value: next },
                                                } as React.ChangeEvent<HTMLInputElement>;
                                                onChange?.(syntheticEvent);
                                                setCountryOpen(false);
                                            }}
                                        >
                                            <CircleFlag countryCode={(c.alpha2 || '').toLowerCase()} height={14} />
                                            <span className="flex-1 truncate">{c.name}</span>
                                            <span className="text-muted-foreground text-xs">{c.countryCallingCodes?.[0]}</span>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </PopoverContent>
                    </Popover>
                )}
                <input
                    ref={ref}
                    value={value}
                    onChange={handlePhoneChange}
                    placeholder={placeholder || "Enter number"}
                    type="tel"
                    autoComplete="tel"
                    name="phone"
                    className={cn(
                        "flex w-full border-none bg-transparent text-base transition-colors placeholder:text-muted-foreground outline-none h-9 py-1 p-0 leading-none md:text-sm [interpolate-size:allow-keywords]",
                        className
                    )}
                    {...props}
                />
            </div>
        );
    }
);

PhoneInput.displayName = "PhoneInput";