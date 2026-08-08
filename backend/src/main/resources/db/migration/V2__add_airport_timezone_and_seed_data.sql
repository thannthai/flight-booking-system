-- Migration V2: Add timezone column to airports table and seed airport master data

ALTER TABLE airports ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh';

-- Seed / Insert Master Data for Domestic, Asian, and International Airports
INSERT INTO airports (iata_code, name, city, country, timezone, latitude, longitude, created_at) VALUES
-- Vietnam Airports
('SGN', 'Sân bay Quốc tế Tân Sơn Nhất', 'Hồ Chí Minh', 'Vietnam', 'Asia/Ho_Chi_Minh', 10.818500, 106.665500, NOW()),
('HAN', 'Sân bay Quốc tế Nội Bài', 'Hà Nội', 'Vietnam', 'Asia/Ho_Chi_Minh', 21.221200, 105.807200, NOW()),
('DAD', 'Sân bay Quốc tế Đà Nẵng', 'Đà Nẵng', 'Vietnam', 'Asia/Ho_Chi_Minh', 16.043900, 108.199400, NOW()),
('CXR', 'Sân bay Quốc tế Cam Ranh', 'Khánh Hòa', 'Vietnam', 'Asia/Ho_Chi_Minh', 11.998100, 109.219400, NOW()),
('PQC', 'Sân bay Quốc tế Phú Quốc', 'Kiên Giang', 'Vietnam', 'Asia/Ho_Chi_Minh', 10.169800, 103.993100, NOW()),
('HPH', 'Sân bay Quốc tế Cát Bi', 'Hải Phòng', 'Vietnam', 'Asia/Ho_Chi_Minh', 20.819400, 106.724700, NOW()),
('VCA', 'Sân bay Quốc tế Cần Thơ', 'Cần Thơ', 'Vietnam', 'Asia/Ho_Chi_Minh', 10.085100, 105.711900, NOW()),
('HUI', 'Sân bay Quốc tế Phú Bài', 'Thừa Thiên Huế', 'Vietnam', 'Asia/Ho_Chi_Minh', 16.401100, 107.702800, NOW()),

-- Asian International Airports
('NRT', 'Sân bay Quốc tế Narita', 'Tokyo', 'Japan', 'Asia/Tokyo', 35.772000, 140.392900, NOW()),
('HND', 'Sân bay Quốc tế Haneda', 'Tokyo', 'Japan', 'Asia/Tokyo', 35.549400, 139.779800, NOW()),
('KIX', 'Sân bay Quốc tế Kansai', 'Osaka', 'Japan', 'Asia/Tokyo', 34.434700, 135.244200, NOW()),
('ICN', 'Sân bay Quốc tế Incheon', 'Seoul', 'South Korea', 'Asia/Seoul', 37.460200, 126.440700, NOW()),
('PUS', 'Sân bay Quốc tế Gimhae', 'Busan', 'South Korea', 'Asia/Seoul', 35.179500, 128.938200, NOW()),
('SIN', 'Sân bay Quốc tế Changi', 'Singapore', 'Singapore', 'Asia/Singapore', 1.364400, 103.991500, NOW()),
('BKK', 'Sân bay Quốc tế Suvarnabhumi', 'Bangkok', 'Thailand', 'Asia/Bangkok', 13.690000, 100.750100, NOW()),
('HKG', 'Sân bay Quốc tế Hồng Kông', 'Hong Kong', 'Hong Kong', 'Asia/Hong_Kong', 22.308000, 113.918500, NOW()),
('KUL', 'Sân bay Quốc tế Kuala Lumpur', 'Kuala Lumpur', 'Malaysia', 'Asia/Kuala_Lumpur', 2.745600, 101.709900, NOW()),
('TPE', 'Sân bay Quốc tế Đào Viên', 'Đài Bắc', 'Taiwan', 'Asia/Taipei', 25.079700, 121.234200, NOW()),
('PEK', 'Sân bay Quốc tế Thủ đô Bắc Kinh', 'Bắc Kinh', 'China', 'Asia/Shanghai', 40.079900, 116.603100, NOW()),
('PVG', 'Sân bay Quốc tế Phố Đông Thượng Hải', 'Thượng Hải', 'China', 'Asia/Shanghai', 31.144300, 121.808300, NOW()),

-- Western / Global International Airports
('LAX', 'Sân bay Quốc tế Los Angeles', 'Los Angeles', 'United States', 'America/Los_Angeles', 33.941600, -118.408500, NOW()),
('JFK', 'Sân bay Quốc tế John F. Kennedy', 'New York', 'United States', 'America/New_York', 40.641300, -73.778100, NOW()),
('SFO', 'Sân bay Quốc tế San Francisco', 'San Francisco', 'United States', 'America/Los_Angeles', 37.621300, -122.379000, NOW()),
('LHR', 'Sân bay Quốc tế Heathrow', 'London', 'United Kingdom', 'Europe/London', 51.470000, -0.454300, NOW()),
('CDG', 'Sân bay Quốc tế Charles de Gaulle', 'Paris', 'France', 'Europe/Paris', 49.009700, 2.547900, NOW()),
('FRA', 'Sân bay Quốc tế Frankfurt', 'Frankfurt', 'Germany', 'Europe/Berlin', 50.037900, 8.562200, NOW()),
('SYD', 'Sân bay Quốc tế Sydney Kingsford Smith', 'Sydney', 'Australia', 'Australia/Sydney', -33.939900, 151.175300, NOW()),
('MEL', 'Sân bay Quốc tế Melbourne', 'Melbourne', 'Australia', 'Australia/Melbourne', -37.669000, 144.841000, NOW())
ON CONFLICT (iata_code) DO UPDATE 
SET timezone = EXCLUDED.timezone,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;
