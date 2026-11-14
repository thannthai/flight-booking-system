package com.thanh.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrades a database that already holds data at the last released schema (V10) to the latest
 * migration, instead of only migrating an empty database. Uses its own PostgreSQL container and
 * never touches the shared Spring test context.
 *
 * The tests are consecutive steps on one database, so their order is fixed.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationUpgradeIT {
    private static final String LAST_RELEASED_VERSION = "10";
    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate db;
    private static Flyway flyway;

    private static long bookingId;
    private static long successPaymentId;

    @BeforeAll
    static void migrateToV10AndSeedData() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(LAST_RELEASED_VERSION).load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(LAST_RELEASED_VERSION);
        seedBusinessData();
        // From here on, upgrade to the newest migration.
        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }

    private static long insert(String sql, Object... args) {
        return db.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    private static void seedBusinessData() {
        long user = insert("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('legacy@example.invalid','dummy','Legacy User','0900000300','ROLE_USER')");
        db.update("INSERT INTO airports(iata_code,name,city,country,timezone) VALUES ('MGA','Origin','Origin','Vietnam','Asia/Ho_Chi_Minh'),('MGB','Destination','Destination','Vietnam','Asia/Ho_Chi_Minh') ON CONFLICT DO NOTHING");
        long airline = insert("INSERT INTO airlines(code,name) VALUES ('MG','Migration Air')");
        long aircraft = insert("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Legacy',6,6,'MIG-001')", airline);
        long flight = insert("""
                INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,departure_time,arrival_time,base_price,source)
                VALUES ('MG001',?,?,(SELECT id FROM airports WHERE iata_code='MGA'),(SELECT id FROM airports WHERE iata_code='MGB'),
                now()+interval '3 days',now()+interval '3 days 2 hours',1500000,'MANUAL')
                """, airline, aircraft);
        long seat = insert("INSERT INTO flight_seats(flight_id,seat_no,cabin_class,status,price) VALUES (?,'1A','ECONOMY','BOOKED',1500000)", flight);
        bookingId = insert("""
                INSERT INTO booking(pnr,user_id,trip_type,base_amount,tax_amount,discount_amount,total_amount,status,payment_deadline,contact_name,contact_email,contact_phone)
                VALUES ('LEGACY',?,'ONE_WAY',1500000,150000,0,1650000,'COMPLETED',now()+interval '1 day','Legacy User','legacy@example.invalid','0900000300')
                """, user);
        long leg = insert("INSERT INTO booking_legs(booking_id,flight_id,leg_order,direction) VALUES (?,?,1,'OUTBOUND')", bookingId, flight);
        long passenger = insert("INSERT INTO passengers(user_id,full_name,date_of_birth,gender,nationality) VALUES (?,'Legacy Passenger','1990-01-01','MALE','Vietnam')", user);
        db.update("INSERT INTO passenger_documents(passenger_id,identity_type,identity_number) VALUES (?,'PASSPORT','LEGACY-DOC-1')", passenger);
        long ticket = insert("""
                INSERT INTO tickets(booking_id,booking_leg_id,passenger_id,flight_seat_id,ticket_no,passenger_type,price,status,issued_at)
                VALUES (?,?,?,?,'MG-LEGACY-1-01','ADULT',1500000,'ISSUED',now())
                """, bookingId, leg, passenger, seat);
        db.update("INSERT INTO add_on_services(ticket_id,service_type,description,price,quantity) VALUES (?,'MEAL','Hot meal',150000,1)", ticket);
        successPaymentId = insert("""
                INSERT INTO payments(booking_id,provider,provider_txn_id,amount,status,order_code,paid_at,refund_status)
                VALUES (?,'PAYOS','LEGACY-TXN-1',1650000,'SUCCESS',2000000001,now(),'NONE')
                """, bookingId);
        db.update("INSERT INTO notifications(booking_id,user_id,type,recipient_email,status) VALUES (?,?,'TICKET_ISSUED','legacy@example.invalid','SENT')", bookingId, user);
    }

    @Test
    @Order(1)
    void upgradeKeepsExistingDataAndAppliesPendingMigrations() {
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();
        assertThat(flyway.info().pending()).isEmpty();
        for (MigrationInfo migration : flyway.info().all()) {
            assertThat(migration.getState().isApplied()).as("migration %s", migration.getVersion()).isTrue();
        }
        assertThat(flyway.info().current().getVersion().getVersion()).isNotEqualTo(LAST_RELEASED_VERSION);

        // Rows written under V10 are untouched.
        assertThat(db.queryForObject("SELECT total_amount FROM booking WHERE id=?", java.math.BigDecimal.class, bookingId))
                .isEqualByComparingTo("1650000");
        assertThat(db.queryForObject("SELECT status FROM booking WHERE id=?", String.class, bookingId)).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT count(*) FROM tickets WHERE booking_id=? AND status='ISSUED'", Integer.class, bookingId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM add_on_services", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM passenger_documents WHERE identity_number='LEGACY-DOC-1'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(1);
        var payment = db.queryForMap("SELECT provider, status, order_code, provider_txn_id, refund_status FROM payments WHERE id=?", successPaymentId);
        assertThat(payment).containsEntry("provider", "PAYOS").containsEntry("status", "SUCCESS")
                .containsEntry("order_code", 2000000001L).containsEntry("provider_txn_id", "LEGACY-TXN-1")
                .containsEntry("refund_status", "NONE");

        // Columns added by the newer migrations are present and empty for legacy rows.
        var added = db.queryForMap("SELECT bank_bin, account_number, account_name, transfer_description, transfer_amount FROM payments WHERE id=?", successPaymentId);
        assertThat(added.values()).containsOnlyNulls();
        assertThat(db.queryForObject("SELECT count(*) FROM users WHERE demo_alias IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    @Order(2)
    void upgradedSchemaEnforcesTheNewConstraints() {
        flyway.migrate();

        // DEMO is now an accepted payment provider (V11); an existing booking can carry one.
        db.update("""
                INSERT INTO payments(booking_id,provider,amount,status,order_code,refund_status)
                VALUES (?,'DEMO',1650000,'CANCELLED',2000000002,'NONE')
                """, bookingId);
        assertThatThrownBy(() -> db.update("""
                INSERT INTO payments(booking_id,provider,amount,status,order_code,refund_status)
                VALUES (?,'UNKNOWN',1,'CANCELLED',2000000003,'NONE')
                """, bookingId)).isInstanceOf(DataIntegrityViolationException.class);

        // Demo alias is restricted to A/B and unique.
        db.update("INSERT INTO users(email,password_hash,full_name,phone,role,demo_alias) VALUES ('demo-a@example.invalid','dummy','Demo A','0900000301','ROLE_USER','A')");
        assertThatThrownBy(() -> db.update("INSERT INTO users(email,password_hash,full_name,phone,role,demo_alias) VALUES ('demo-a2@example.invalid','dummy','Demo A2','0900000302','ROLE_USER','A')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO users(email,password_hash,full_name,phone,role,demo_alias) VALUES ('demo-c@example.invalid','dummy','Demo C','0900000303','ROLE_USER','C')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(3)
    void migrationsAreIdempotentOnceUpToDate() {
        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }
}
