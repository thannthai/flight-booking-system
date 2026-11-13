package com.thanh.backend.flight.service;

import com.thanh.backend.AbstractIntegrationTest;
import com.thanh.backend.flight.demo.*;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.controller.FlightController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class FlightSearchIT extends AbstractIntegrationTest {
    @Autowired FlightService service;
    @Autowired DemoFlightRepository demo;
    @Autowired FlightController controller;
    @Test void timezoneAndUpcomingFilterPreserveAdminHistory() {
        Long airline=jdbcTemplate.queryForObject("INSERT INTO airlines(code,name) VALUES ('TZ','Timezone') RETURNING id",Long.class);
        Long aircraft=jdbcTemplate.queryForObject("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Demo',180,180,'TZ-01') RETURNING id",Long.class,airline);
        for(String code:new String[]{"SGN","SIN","HAN","ICN"}) {
            String zone=code.equals("SIN")?"Asia/Singapore":code.equals("ICN")?"Asia/Seoul":"Asia/Ho_Chi_Minh";
            jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country,timezone) VALUES (?,?,?,'Demo',?) ON CONFLICT(iata_code) DO NOTHING",code,code,code,zone);
        }
        var sgn=demo.airport("SGN");var sin=demo.airport("SIN");
        Instant departure=LocalDate.of(2027,2,1).atTime(9,0).atZone(sgn.zone()).toInstant();
        assertEquals(LocalTime.of(12,5),departure.plusSeconds(125*60).atZone(sin.zone()).toLocalTime());
        var han=demo.airport("HAN");var icn=demo.airport("ICN");
        Instant korea=LocalDate.of(2027,2,1).atTime(8,0).atZone(han.zone()).toInstant();
        assertEquals(LocalTime.of(14,20),korea.plusSeconds(260*60).atZone(icn.zone()).toLocalTime());
        for(int days:new int[]{-2,2}) jdbcTemplate.update("""
                INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,
                departure_time,arrival_time,base_price,source) VALUES (?,?,?,?,?,now()+(?*interval '1 day'),now()+(?*interval '1 day')+interval '2 hours',100000,'MANUAL')
                ""","TZ"+days,airline,aircraft,sgn.id(),sin.id(),days,days);
        assertEquals(2,service.searchFlights("SGN","SIN",null,FlightStatus.SCHEDULED,PageRequest.of(0,1)).getTotalElements());
        var upcoming=service.searchFlights("SGN","SIN",null,FlightStatus.SCHEDULED,PageRequest.of(0,1),true);
        assertEquals(1,upcoming.getTotalElements());assertEquals("TZ2",upcoming.getContent().getFirst().getFlightNo());
        var response=controller.searchFlights("SGN","SIN",null,PageRequest.of(0,1));
        assertEquals(1,response.getData().getTotalElements());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE flights SET source='CRAWLER' WHERE flight_no='TZ2'"));
        assertEquals("MANUAL", jdbcTemplate.queryForObject("SELECT source FROM flights WHERE flight_no='TZ2'",String.class));
    }

    @Test void searchWorksWhenOriginOrDestinationIsOmitted() {
        Long airline=jdbcTemplate.queryForObject("INSERT INTO airlines(code,name) VALUES ('OM','Optional') RETURNING id",Long.class);
        Long aircraft=jdbcTemplate.queryForObject("INSERT INTO aircrafts(airline_code_id,model,total_seats,economy_seats,registration_no) VALUES (?,'Demo',180,180,'OM-01') RETURNING id",Long.class,airline);
        for(String code:new String[]{"SGN","SIN","HAN","ICN"})
            jdbcTemplate.update("INSERT INTO airports(iata_code,name,city,country,timezone) VALUES (?,?,?,'Demo','UTC') ON CONFLICT(iata_code) DO NOTHING",code,code,code);
        String[][] routes={{"OM1","SGN","SIN"},{"OM2","HAN","ICN"}};
        for(String[] route:routes) jdbcTemplate.update("""
                INSERT INTO flights(flight_no,airline_code_id,aircraft_id,origin_iata_id,dest_iata_id,
                departure_time,arrival_time,base_price,source) VALUES (?,?,?,(SELECT id FROM airports WHERE iata_code=?),(SELECT id FROM airports WHERE iata_code=?),
                now()+interval '2 days',now()+interval '2 days 2 hours',100000,'MANUAL')
                """,route[0],airline,aircraft,route[1],route[2]);
        var page=PageRequest.of(0,10);
        assertEquals(2,service.searchFlights(null,null,null,FlightStatus.SCHEDULED,page,true).getTotalElements());
        assertEquals(2,service.searchFlights("  ","",null,FlightStatus.SCHEDULED,page,true).getTotalElements());
        assertEquals("OM1",service.searchFlights("sgn",null,null,FlightStatus.SCHEDULED,page,true).getContent().getFirst().getFlightNo());
        assertEquals("OM2",service.searchFlights(null,"ICN",null,FlightStatus.SCHEDULED,page,true).getContent().getFirst().getFlightNo());
        // Page smaller than the result set forces the separate count query to run with null filters too.
        assertEquals(2,service.searchFlights(null,null,null,FlightStatus.SCHEDULED,PageRequest.of(0,1),true).getTotalElements());
        assertEquals(0,service.searchFlights("SGN","ICN",null,FlightStatus.SCHEDULED,page,true).getTotalElements());
    }
}
