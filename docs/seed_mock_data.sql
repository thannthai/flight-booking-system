-- ====================================================================
-- SCRIPT SEED DỮ LIỆU CHUẨN THỰC TẾ & IDEMPOTENT (DYNAMIC DATES)
-- Hệ thống: Flight Booking System
-- Hỗ trợ: Supabase Cloud PostgreSQL
-- Có thể chạy nhiều lần, dữ liệu không bị duplicate
-- ====================================================================

-- 1. BẬT EXTENSION UNACCENT ĐỂ TÌM KIẾM TIẾNG VIỆT KHÔNG DẤU
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Đảm bảo không bao giờ trùng ghế trên cùng 1 chuyến bay (bắt buộc để ON CONFLICT hoạt động)
CREATE UNIQUE INDEX IF NOT EXISTS uq_flight_seats_flight_seat
ON flight_seats (flight_id, seat_no);

-- Đảm bảo một số hiệu chuyến bay không bị duplicate cùng thời điểm khởi hành
CREATE UNIQUE INDEX IF NOT EXISTS uq_flights_flight_no_dep_time
ON flights (flight_no, departure_time);

-- ====================================================================
-- 2. SEED TÀI KHOẢN MẪU (USERS)
-- Mật khẩu đăng nhập của cả 3 tài khoản: Password123@
-- ====================================================================
INSERT INTO users (email, password_hash, full_name, phone, role, created_at)
VALUES 
('admin@flight.com', '$2a$10$sXNEJGFUMNgokZkpo8z3yeT2hBab8FxLTjY3KJe3FpQQ9wL5yrC3e', 'System Admin', '0987654321', 'ROLE_ADMIN', NOW()),
('user1@flight.com', '$2a$10$gfftE.EDcJK.IMssDs.8BOylHaYunUGPz.3aH06g8gWyg9XZwhhxO', 'Nguyen Van A', '0912345678', 'ROLE_USER', NOW()),
('user2@flight.com', '$2a$10$vrHG5eyjDScUUbPUm1krjeXL0f1xY5FaEGBYDHJdlHsIgIHaauYWu', 'Tran Thi B', '0934567890', 'ROLE_USER', NOW())
ON CONFLICT (email) DO NOTHING;

-- ====================================================================
-- 3. SEED HÃNG HÀNG KHÔNG (AIRLINES)
-- ====================================================================
INSERT INTO airlines (code, name, logo_url, free_baggage, extra_baggage_fee, created_at)
VALUES
('VN', 'Vietnam Airlines', 'https://cdn.haitrieu.com/wp-content/uploads/2022/01/Logo-VNA-Sky-Te-Bl-V.png', true, 200000.00, NOW()),
('VJ', 'VietJet Air', 'https://cdn.haitrieu.com/wp-content/uploads/2022/01/Logo-VietJet-Air-Red.png', false, 150000.00, NOW()),
('QH', 'Bamboo Airways', 'https://cdn.haitrieu.com/wp-content/uploads/2022/01/Logo-Bamboo-Airways-Green.png', true, 180000.00, NOW())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    logo_url = EXCLUDED.logo_url,
    free_baggage = EXCLUDED.free_baggage,
    extra_baggage_fee = EXCLUDED.extra_baggage_fee;

-- ====================================================================
-- 3b. SEED VOUCHERS (MÃ GIẢM GIÁ MẪU)
-- ====================================================================
INSERT INTO vouchers (code, discount_type, value, usage_limit, used_count, valid_from, valid_until, created_at)
VALUES 
('BAYHE2026', 'PERCENT', 10.00, 100, 0, CURRENT_DATE, CURRENT_DATE + 90, NOW()),
('CHAOMUNG', 'FIXED', 100000.00, 50, 0, CURRENT_DATE, CURRENT_DATE + 90, NOW())
ON CONFLICT (code) DO NOTHING;

-- ====================================================================
-- 4. SEED CÁC DÒNG MÁY BAY (AIRCRAFTS)
-- ====================================================================
INSERT INTO aircrafts (airline_code_id, model, total_seats, business_seats, economy_seats, registration_no, created_at)
VALUES
((SELECT id FROM airlines WHERE code = 'VN' LIMIT 1), 'Airbus A321-200', 180, 24, 156, 'VN-A321', NOW()),
((SELECT id FROM airlines WHERE code = 'VJ' LIMIT 1), 'Airbus A320-200', 180, 0, 180, 'VJ-A320', NOW()),
((SELECT id FROM airlines WHERE code = 'QH' LIMIT 1), 'Boeing 787-9 Dreamliner', 294, 30, 264, 'QH-B787', NOW())
ON CONFLICT (registration_no) DO UPDATE
SET model = EXCLUDED.model,
    total_seats = EXCLUDED.total_seats,
    business_seats = EXCLUDED.business_seats,
    economy_seats = EXCLUDED.economy_seats;

-- ====================================================================
-- 5. FUNCTION TỰ ĐỘNG SINH SƠ ĐỒ GHẾ THEO CHUẨN CỦA BACKEND
-- Business: Cột A, C, D, F (Giá = 2.5 x basePrice)
-- Economy:  Cột A, B, C, D, E, F (Giá = basePrice)
-- ====================================================================
CREATE OR REPLACE FUNCTION fn_generate_flight_seats(
    p_flight_id BIGINT,
    p_business_count INT,
    p_economy_count INT,
    p_base_price NUMERIC(12, 2)
) RETURNS VOID AS $$
DECLARE
    biz_cols TEXT[] := ARRAY['A', 'C', 'D', 'F'];
    eco_cols TEXT[] := ARRAY['A', 'B', 'C', 'D', 'E', 'F'];
    biz_price NUMERIC(12, 2) := p_base_price * 2.5;
    curr_row INT := 1;
    col_idx INT := 1;
    i INT;
BEGIN
    -- 1. Sinh ghế Business
    IF p_business_count > 0 THEN
        FOR i IN 1..p_business_count LOOP
            INSERT INTO flight_seats (flight_id, seat_no, cabin_class, price, status, created_at)
            VALUES (
                p_flight_id,
                curr_row || biz_cols[col_idx],
                'BUSINESS',
                biz_price,
                'AVAILABLE',
                NOW()
            ) ON CONFLICT (flight_id, seat_no) DO NOTHING;

            col_idx := col_idx + 1;
            IF col_idx > array_length(biz_cols, 1) THEN
                col_idx := 1;
                curr_row := curr_row + 1;
            END IF;
        END LOOP;
        IF col_idx > 1 THEN
            curr_row := curr_row + 1;
        END IF;
    END IF;

    -- 2. Sinh ghế Economy
    col_idx := 1;
    IF p_economy_count > 0 THEN
        FOR i IN 1..p_economy_count LOOP
            INSERT INTO flight_seats (flight_id, seat_no, cabin_class, price, status, created_at)
            VALUES (
                p_flight_id,
                curr_row || eco_cols[col_idx],
                'ECONOMY',
                p_base_price,
                'AVAILABLE',
                NOW()
            ) ON CONFLICT (flight_id, seat_no) DO NOTHING;

            col_idx := col_idx + 1;
            IF col_idx > array_length(eco_cols, 1) THEN
                col_idx := 1;
                curr_row := curr_row + 1;
            END IF;
        END LOOP;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ====================================================================
-- 6. TỰ ĐỘNG SINH CÁC CHUYẾN BAY CHO 7 NGÀY TỚI (DYNAMIC ROLLING DATES)
-- Ngày bay tự tính từ CURRENT_DATE + 1 ngày cho tới CURRENT_DATE + 7 ngày
-- ====================================================================
DO $$
DECLARE
    d INT;
    target_date DATE;
    v_origin_sgn BIGINT;
    v_dest_han BIGINT;
    v_dest_dad BIGINT;
    v_dest_pqc BIGINT;
    v_air_vn BIGINT;
    v_air_vj BIGINT;
    v_air_qh BIGINT;
    v_craft_vn BIGINT;
    v_craft_vj BIGINT;
    v_craft_qh BIGINT;
    v_flight_id BIGINT;
BEGIN
    SELECT id INTO v_origin_sgn FROM airports WHERE iata_code = 'SGN' LIMIT 1;
    SELECT id INTO v_dest_han   FROM airports WHERE iata_code = 'HAN' LIMIT 1;
    SELECT id INTO v_dest_dad   FROM airports WHERE iata_code = 'DAD' LIMIT 1;
    SELECT id INTO v_dest_pqc   FROM airports WHERE iata_code = 'PQC' LIMIT 1;

    SELECT id INTO v_air_vn FROM airlines WHERE code = 'VN' LIMIT 1;
    SELECT id INTO v_air_vj FROM airlines WHERE code = 'VJ' LIMIT 1;
    SELECT id INTO v_air_qh FROM airlines WHERE code = 'QH' LIMIT 1;

    SELECT id INTO v_craft_vn FROM aircrafts WHERE registration_no = 'VN-A321' LIMIT 1;
    SELECT id INTO v_craft_vj FROM aircrafts WHERE registration_no = 'VJ-A320' LIMIT 1;
    SELECT id INTO v_craft_qh FROM aircrafts WHERE registration_no = 'QH-B787' LIMIT 1;

    -- Lặp qua 7 ngày tới (từ ngày mai d=1 đến 7 ngày sau d=7)
    FOR d IN 1..7 LOOP
        target_date := CURRENT_DATE + d;

        -- 1. SGN -> HAN (VN204 - Vietnam Airlines 06:00 -> 08:15)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VN204', v_air_vn, v_craft_vn, v_origin_sgn, v_dest_han, (target_date + TIME '06:00:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '08:15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 1500000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 24, 156, 1500000.00);

        -- 2. SGN -> HAN (VJ130 - VietJet Air 09:30 -> 11:45)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VJ130', v_air_vj, v_craft_vj, v_origin_sgn, v_dest_han, (target_date + TIME '09:30:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '11:45:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 990000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 0, 180, 990000.00);

        -- 3. SGN -> HAN (QH152 - Bamboo Airways 14:00 -> 16:15)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('QH152', v_air_qh, v_craft_qh, v_origin_sgn, v_dest_han, (target_date + TIME '14:00:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '16:15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 1350000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 30, 264, 1350000.00);

        -- 4. HAN -> SGN (VN205 - Vietnam Airlines 11:00 -> 13:15)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VN205', v_air_vn, v_craft_vn, v_dest_han, v_origin_sgn, (target_date + TIME '11:00:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '13:15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 1500000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 24, 156, 1500000.00);

        -- 5. SGN -> DAD (VN112 - Vietnam Airlines 08:00 -> 09:25)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VN112', v_air_vn, v_craft_vn, v_origin_sgn, v_dest_dad, (target_date + TIME '08:00:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '09:25:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 1100000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 24, 156, 1100000.00);

        -- 6. DAD -> SGN (VJ623 - VietJet Air 16:00 -> 17:25)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VJ623', v_air_vj, v_craft_vj, v_dest_dad, v_origin_sgn, (target_date + TIME '16:00:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '17:25:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 750000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 0, 180, 750000.00);

        -- 7. SGN -> PQC (VJ325 - VietJet Air 10:30 -> 11:35)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VJ325', v_air_vj, v_craft_vj, v_origin_sgn, v_dest_pqc, (target_date + TIME '10:30:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '11:35:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 650000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 0, 180, 650000.00);

        -- 8. PQC -> SGN (VN1824 - Vietnam Airlines 13:45 -> 14:50)
        INSERT INTO flights (flight_no, airline_code_id, aircraft_id, origin_iata_id, dest_iata_id, departure_time, arrival_time, status, base_price, source, created_at)
        VALUES ('VN1824', v_air_vn, v_craft_vn, v_dest_pqc, v_origin_sgn, (target_date + TIME '13:45:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', (target_date + TIME '14:50:00') AT TIME ZONE 'Asia/Ho_Chi_Minh', 'SCHEDULED', 850000.00, 'MANUAL', NOW())
        ON CONFLICT (flight_no, departure_time) DO UPDATE SET base_price = EXCLUDED.base_price
        RETURNING id INTO v_flight_id;
        PERFORM fn_generate_flight_seats(v_flight_id, 24, 156, 850000.00);

    END LOOP;
END $$;
