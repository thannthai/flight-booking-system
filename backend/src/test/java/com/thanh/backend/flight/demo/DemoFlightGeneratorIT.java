package com.thanh.backend.flight.demo;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.flight.repository.FlightRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles({"test","demo"})
class DemoFlightGeneratorIT extends AbstractIntegrationTest {
    @Autowired DemoFlightProperties properties;
    @Autowired DemoFlightConfiguration safety;
    @Autowired DemoFlightTemplates templates;
    @Autowired DemoFlightWriter writer;
    @Autowired Environment environment;
    @Autowired FlightRepository flights;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockitoBean(name="demoFlightClock") Clock clock;
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2027-01-31T17:00:00Z"));
    DemoFlightGenerator generator;

    @BeforeEach void fixture() {
        now.set(Instant.parse("2027-01-31T17:00:00Z"));
        when(clock.instant()).thenAnswer(i->now.get());
        when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
        while(properties.getSchedules().size()>1) properties.getSchedules().removeLast();
        properties.setEnabled(true);
        properties.setAllowedJdbcUrl(environment.getProperty("spring.datasource.url"));
        properties.setMaxFlightsPerDay(12);
        properties.setMaxWindowSeats(90000);
        generator=new DemoFlightGenerator(safety,properties,templates,writer,clock);
        for(String airport:new String[]{"SGN","HAN","DAD","SIN","BKK","ICN"}) {
            String zone=airport.equals("SIN")?"Asia/Singapore":airport.equals("ICN")?"Asia/Seoul":airport.equals("BKK")?"Asia/Bangkok":"Asia/Ho_Chi_Minh";
            jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country,timezone) VALUES (?,?,?,'Demo',?) ON CONFLICT(iata_code) DO NOTHING",airport,airport,airport,zone);
        }
        for(String code:new String[]{"VN","VJ","QH"}) {
            Long id=jdbcTemplate.queryForObject("INSERT INTO airlines(code,name) VALUES (?,?) RETURNING id",Long.class,code,code);
            boolean qh=code.equals("QH"),vj=code.equals("VJ");
            jdbcTemplate.update("INSERT INTO aircrafts(airline_code_id,model,total_seats,business_seats,economy_seats,registration_no) VALUES (?,'Demo fixture',?,?,?,?)",id,qh?294:180,qh?30:vj?0:24,qh?264:vj?180:156,code.equals("VN")?"VN-A321":vj?"VJ-A320":"QH-B787");
        }
    }
    @AfterEach void resetFlag() { properties.setEnabled(false); }
    int count(String table) { return jdbcTemplate.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    DemoFlightTemplates.Pair first() { return templates.pairs(properties,LocalDate.of(2027,2,2)).getFirst(); }

    @Test void coverageIdempotencyAndIndependentConcurrentRuns() throws Exception {
        var workers=Executors.newFixedThreadPool(2);
        try {
            // Separate orchestrators model separate application instances; writer transactions are real.
            var other=new DemoFlightGenerator(safety,properties,templates,writer,clock);
            var a=workers.submit(generator::reconcile);var b=workers.submit(other::reconcile);
            a.get(45,TimeUnit.SECONDS);b.get(45,TimeUnit.SECONDS);
        } finally {workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
        generator.reconcile();
        assertEquals(360,count("flights"));assertEquals(71640,count("flight_seats"));
        assertEquals(3,jdbcTemplate.queryForObject("SELECT count(DISTINCT airline_code_id) FROM flights",Integer.class));
        assertEquals(0,jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT flight_id,seat_no FROM flight_seats GROUP BY 1,2 HAVING count(*)>1) x",Integer.class));
        assertEquals(0,jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT (departure_time AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM flights GROUP BY 1 HAVING count(*)>12) x",Integer.class));
        var next=new DemoFlightProperties.Schedule();next.setValidFrom(LocalDate.of(2027,3,3));
        next.setAirlines(new java.util.ArrayList<>(properties.getSchedules().getFirst().getAirlines()));
        var extra=json.convertValue(next.getAirlines().getFirst(),DemoFlightProperties.Airline.class);
        extra.setCode("ZZ");extra.setAircraft(java.util.List.of("MISSING"));
        extra.getRoutes().get(0).setOutboundSlot("Z1");extra.getRoutes().get(0).setReturnSlot("Z2");
        extra.getRoutes().get(1).setOutboundSlot("Z3");extra.getRoutes().get(1).setReturnSlot("Z4");
        next.getAirlines().add(extra);properties.getSchedules().add(next);
        jdbcTemplate.update("INSERT INTO airlines(code,name) VALUES ('ZZ','Configured new airline')");
        now.set(now.get().plus(Duration.ofDays(1)));generator.reconcile();
        assertEquals(2,jdbcTemplate.queryForObject("SELECT count(*) FROM flights f JOIN airlines a ON a.id=f.airline_code_id WHERE a.code='ZZ'",Integer.class));
        assertEquals(1,jdbcTemplate.queryForObject("SELECT count(*) FROM aircrafts WHERE registration_no='DEMO-ZZ-RZ1'",Integer.class));
        assertEquals(372,count("flights"));

    }
    @Test void allConfiguredRoutesRunDailyWithoutAircraftOverlap() {
        generator.reconcile();
        assertEquals(360, count("flights"));
        assertEquals(12, jdbcTemplate.queryForObject("SELECT count(DISTINCT (origin_iata_id,dest_iata_id)) FROM flights", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT origin_iata_id,dest_iata_id FROM flights GROUP BY 1,2 HAVING count(*)<>30) x", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM flights a JOIN flights b ON a.aircraft_id=b.aircraft_id AND a.id<b.id
                WHERE a.departure_time < b.arrival_time + interval '1 hour'
                  AND b.departure_time < a.arrival_time + interval '1 hour'
                """, Integer.class));
    }
    @Test void fillsLegacyAlternatingScheduleWithoutChangingExistingFlightsOrSeats() {
        var schedule = properties.getSchedules().getFirst();
        LocalDate today = LocalDate.of(2027,2,1);
        // Simulate a deployment that used the original shared fallback aircraft.
        var registrations = new java.util.HashMap<String,java.util.List<String>>();
        for (var airline : schedule.getAirlines()) {
            registrations.put(airline.getCode(),airline.getAircraft());
            String legacy = "DEMO-"+airline.getCode()+"-01";
            jdbcTemplate.update("UPDATE aircrafts SET registration_no=?,model='DEMO regional layout',total_seats=180,business_seats=24,economy_seats=156 WHERE airline_code_id=(SELECT id FROM airlines WHERE code=?)",legacy,airline.getCode());
            airline.setAircraft(java.util.List.of(legacy));
        }
        try {
            for (int day = 0; day < 30; day++) {
                LocalDate date = today.plusDays(day);
                int route = (int) (java.time.temporal.ChronoUnit.DAYS.between(schedule.getValidFrom(),date) % 2);
                for (var airline : schedule.getAirlines()) {
                    var pair = new DemoFlightTemplates.Pair(airline,airline.getRoutes().get(route),date);
                    assertTrue(writer.create(pair,false));
                    assertTrue(writer.create(pair,true));
                }
            }
        } finally {
            for (var airline : schedule.getAirlines()) airline.setAircraft(registrations.get(airline.getCode()));
        }
        assertEquals(180,count("flights"));
        var oldFlights = jdbcTemplate.queryForList("SELECT * FROM flights ORDER BY id");
        long lastFlight = jdbcTemplate.queryForObject("SELECT max(id) FROM flights",Long.class);
        var oldSeats = jdbcTemplate.queryForList("SELECT * FROM flight_seats ORDER BY id");
        long lastSeat = jdbcTemplate.queryForObject("SELECT max(id) FROM flight_seats",Long.class);
        generator.reconcile();
        generator.reconcile();
        assertEquals(360,count("flights"));
        assertEquals(oldFlights,jdbcTemplate.queryForList("SELECT * FROM flights WHERE id<=? ORDER BY id",lastFlight));
        assertEquals(oldSeats,jdbcTemplate.queryForList("SELECT * FROM flight_seats WHERE id<=? ORDER BY id",lastSeat));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM flights a JOIN flights b ON a.aircraft_id=b.aircraft_id AND a.id<b.id
                WHERE a.departure_time < b.arrival_time + interval '1 hour'
                  AND b.departure_time < a.arrival_time + interval '1 hour'
                """, Integer.class));
    }
    @Test void atomicFailureAndMissingAirlineAreIsolated() {
        jdbcTemplate.update("DELETE FROM aircrafts WHERE airline_code_id=(SELECT id FROM airlines WHERE code='VN')");
        jdbcTemplate.execute("CREATE FUNCTION reject_demo_seat() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'seat rejected' USING ERRCODE='23514'; END $$");
        jdbcTemplate.execute("CREATE TRIGGER reject_demo_seat BEFORE INSERT ON flight_seats FOR EACH ROW EXECUTE FUNCTION reject_demo_seat()");
        try {
            assertThrows(RuntimeException.class,()->writer.create(first(),false));
            assertEquals(0,count("flights"));assertEquals(0,count("flight_seats"));
            assertEquals(0,jdbcTemplate.queryForObject("SELECT count(*) FROM aircrafts WHERE registration_no LIKE 'DEMO-%'",Integer.class));
        } finally {jdbcTemplate.execute("DROP TRIGGER reject_demo_seat ON flight_seats");jdbcTemplate.execute("DROP FUNCTION reject_demo_seat()");}
        jdbcTemplate.update("DELETE FROM aircrafts WHERE airline_code_id=(SELECT id FROM airlines WHERE code='VJ')");
        jdbcTemplate.update("DELETE FROM airlines WHERE code='VJ'");
        assertDoesNotThrow(generator::reconcile);
        assertEquals(240,count("flights"));
        assertEquals(0,jdbcTemplate.queryForObject("SELECT count(*) FROM flight_seats WHERE status<>'AVAILABLE'",Integer.class));
    }
    @Test void downtimePreservesExistingHistoryAndHeldInventory() {
        assertTrue(writer.create(first(),false));
        Long id=jdbcTemplate.queryForObject("SELECT id FROM flights",Long.class);
        var flight=jdbcTemplate.queryForMap("SELECT * FROM flights WHERE id=?",id);
        Long user=jdbcTemplate.queryForObject("INSERT INTO users(email,password_hash,full_name,phone,role) VALUES ('history@example.invalid','dummy','History','0900000000','ROLE_USER') RETURNING id",Long.class);
        Long booking=jdbcTemplate.queryForObject("INSERT INTO booking(pnr,user_id,trip_type,base_amount,tax_amount,total_amount,status,payment_deadline) VALUES ('DEM001',?,'ONE_WAY',100,10,110,'COMPLETED',now()) RETURNING id",Long.class,user);
        Long leg=jdbcTemplate.queryForObject("INSERT INTO booking_legs(booking_id,flight_id,leg_order,direction) VALUES (?,?,1,'OUTBOUND') RETURNING id",Long.class,booking,id);
        Long passenger=jdbcTemplate.queryForObject("INSERT INTO passengers(user_id,full_name,date_of_birth,gender) VALUES (?,'History','1990-01-01','MALE') RETURNING id",Long.class,user);
        Long seat=jdbcTemplate.queryForObject("SELECT min(id) FROM flight_seats WHERE flight_id=?",Long.class,id);
        jdbcTemplate.update("INSERT INTO tickets(booking_id,booking_leg_id,passenger_id,flight_seat_id,passenger_type,price,status) VALUES (?,?,?,?,'ADULT',100,'ISSUED')",booking,leg,passenger,seat);
        jdbcTemplate.update("INSERT INTO payments(booking_id,provider,amount,status) VALUES (?,'PAYOS',110,'SUCCESS')",booking);
        jdbcTemplate.update("UPDATE flight_seats SET status='BOOKED' WHERE id=?",seat);
        jdbcTemplate.update("UPDATE flight_seats SET status='HELD',held_by_user=?,held_until=now()+interval '15 minutes' WHERE flight_id=? AND id<>?",user,id,seat);
        var seats=jdbcTemplate.queryForList("SELECT * FROM flight_seats WHERE flight_id=? ORDER BY id",id);
        var graph=jdbcTemplate.queryForList("SELECT * FROM payments WHERE booking_id=?",booking);
        now.set(now.get().plus(Duration.ofDays(90)));generator.reconcile();
        assertEquals(flight,jdbcTemplate.queryForMap("SELECT * FROM flights WHERE id=?",id));
        assertEquals(seats,jdbcTemplate.queryForList("SELECT * FROM flight_seats WHERE flight_id=? ORDER BY id",id));
        assertEquals(graph,jdbcTemplate.queryForList("SELECT * FROM payments WHERE booking_id=?",booking));
        assertEquals(1,count("booking"));assertEquals(1,count("tickets"));assertEquals(361,count("flights"));
    }
    @Test void featureFlagsAndBudgetBlockWrites() {
        properties.setMaxFlightsPerDay(13);
        assertThrows(IllegalStateException.class,safety::assertSafe);
        properties.setMaxFlightsPerDay(12);
        var denied=new DemoFlightConfiguration(properties,new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.datasource.url",properties.getAllowedJdbcUrl()));
        assertThrows(IllegalStateException.class,denied::assertSafe);
        properties.setEnabled(false);assertThrows(IllegalStateException.class,()->writer.create(first(),false));
        properties.setEnabled(true);properties.setAllowedJdbcUrl("jdbc:postgresql://not-allowed/demo");
        assertThrows(IllegalStateException.class,generator::reconcile);assertEquals(0,count("flights"));
        properties.setAllowedJdbcUrl(environment.getProperty("spring.datasource.url"));
        assertTrue(writer.create(first(),false));properties.setMaxWindowSeats(180);
        assertThrows(IllegalArgumentException.class,()->writer.create(first(),true));assertEquals(1,count("flights"));
        var route=properties.getSchedules().getFirst().getAirlines().getFirst().getRoutes().getFirst();
        String original=route.getOutboundSlot();
        try {
            route.setOutboundSlot(route.getReturnSlot());
            assertDoesNotThrow(generator::reconcile);
            assertEquals(1,count("flights"));
        } finally {route.setOutboundSlot(original);}

    }
}
