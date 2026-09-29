-- 1. Thêm thông tin liên hệ người đặt vé vào bảng booking
ALTER TABLE booking ADD COLUMN contact_name VARCHAR(255);
ALTER TABLE booking ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE booking ADD COLUMN contact_phone VARCHAR(20);

-- 2. Thêm cột người giám hộ vào bảng passengers
ALTER TABLE passengers ADD COLUMN guardian_id BIGINT;
ALTER TABLE passengers ADD CONSTRAINT fk_passengers_guardian_id FOREIGN KEY (guardian_id) REFERENCES passengers(id);
