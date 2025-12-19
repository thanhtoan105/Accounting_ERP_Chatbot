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

    public VietnameseFaker(long seed) {
        this.faker = new Faker(new Locale("vi"), new java.util.Random(seed));
    }

    public String companyName() {
        String prefix = COMPANY_PREFIXES.get(faker.random().nextInt(COMPANY_PREFIXES.size()));
        String name = COMPANY_NAMES.get(faker.random().nextInt(COMPANY_NAMES.size()));
        return prefix + " " + name;
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

    public Faker getFaker() {
        return faker;
    }
}
