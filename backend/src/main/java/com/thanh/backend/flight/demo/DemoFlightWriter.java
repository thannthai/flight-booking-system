package com.thanh.backend.flight.demo;

import com.thanh.backend.aircraft.entity.Aircraft;
import com.thanh.backend.aircraft.repository.AircraftRepository;
import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.service.FlightSeatLayoutFactory;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class DemoFlightWriter {
    private final DemoFlightConfiguration safety;
    private final DemoFlightProperties properties;
    private final DemoFlightRepository repository;
    private final AircraftRepository aircrafts;
    private final FlightSeatLayoutFactory layout;
    private final Clock clock;
    @PersistenceContext private EntityManager em;
    public DemoFlightWriter(DemoFlightConfiguration safety, DemoFlightProperties properties,
            DemoFlightRepository repository, AircraftRepository aircrafts, FlightSeatLayoutFactory layout,
            @Qualifier("demoFlightClock") Clock clock) {
        this.safety=safety; this.properties=properties; this.repository=repository;
        this.aircrafts=aircrafts; this.layout=layout; this.clock=clock;
    }
    private boolean valid(Aircraft a, long airline) {
        return a.getAirline().getId()==airline && a.getBusinessSeats()>=0 && a.getEconomySeats()>=0
                && a.getTotalSeats()>0 && a.getTotalSeats()<=300
                && a.getBusinessSeats()+a.getEconomySeats()==a.getTotalSeats();
    }
    @Transactional(timeout=15)
    public boolean create(DemoFlightTemplates.Pair pair, boolean returning) {
        safety.assertSafe(); // Must precede every write, including fallback aircraft creation.
        repository.serializeBudget();
        var route=pair.route();
        String no=returning?pair.returnNo():pair.outboundNo();
        if(repository.exists(no)) return false;
        Long airline=repository.airline(pair.airline().getCode());
        if(airline==null) throw new IllegalArgumentException("Demo airline missing");
        var origin=repository.airport(route.getOrigin());
        var dest=repository.airport(route.getDestination());
        Instant outbound=pair.date().atTime(route.getDeparture()).atZone(origin.zone()).toInstant();
        Instant back=outbound.plusSeconds((route.getDurationMinutes()+route.getTurnaroundMinutes())*60L);
        Instant end=back.plusSeconds(route.getDurationMinutes()*60L);
        Instant departure=returning?back:outbound;
        Instant arrival=departure.plusSeconds(route.getDurationMinutes()*60L);
        if(!departure.isAfter(clock.instant().plusSeconds(7200))) return false;
        LocalDate day=departure.atZone(DemoFlightTemplates.ZONE).toLocalDate();
        LocalDate today=LocalDate.now(clock.withZone(DemoFlightTemplates.ZONE));
        if(day.isBefore(today)||!day.isBefore(today.plusDays(30))) return false;
        Aircraft selected=null;
        for(String registration:pair.airline().getAircraft()) {
            var candidate=aircrafts.findByRegistrationNo(registration).orElse(null);
            if(candidate!=null && valid(candidate,airline)
                    && !repository.overlaps(candidate.getId(),outbound,end,pair.outboundNo(),pair.returnNo())) { selected=candidate; break; }
        }
        // A counterpart already generated for this pair can own the selected aircraft.
        String counterpart=returning?pair.outboundNo():pair.returnNo();
        var existing=em.createQuery("SELECT f FROM Flight f WHERE f.flightNo=:no AND f.source=com.thanh.backend.flight.enums.FlightSource.DEMO",Flight.class)
                .setParameter("no",counterpart).getResultList();
        if(!existing.isEmpty()) {
            var a=existing.getFirst().getAircraft();
            if(!valid(a,airline) || repository.overlaps(a.getId(),outbound,end,pair.outboundNo(),pair.returnNo()))
                throw new IllegalArgumentException("Counterpart aircraft no longer suitable");
            selected=a;
        }
        if(selected==null && pair.airline().isFallback()) {
            String registration="DEMO-"+pair.airline().getCode()+"-01";
            repository.fallback(airline,registration);
            selected=aircrafts.findByRegistrationNo(registration).orElseThrow();
            if(!valid(selected,airline)||!selected.getModel().equals("DEMO regional layout")
                    ||selected.getTotalSeats()!=180 || selected.getBusinessSeats()!=24)
                throw new IllegalArgumentException("Demo aircraft identity conflict");
            if(repository.overlaps(selected.getId(),outbound,end,pair.outboundNo(),pair.returnNo())) throw new IllegalArgumentException("Demo aircraft overlap");
        }
        if(selected==null) throw new IllegalArgumentException("No suitable demo aircraft");
        if(!repository.budget(day,today,properties.getMaxFlightsPerDay(),properties.getMaxWindowSeats(),selected.getTotalSeats()))
            throw new IllegalArgumentException("Demo generation budget exceeded");
        Long id=repository.insert(no,airline,selected.getId(),returning?dest.id():origin.id(),
                returning?origin.id():dest.id(),departure,arrival,route.getPrice());
        if(id==null) return false;
        repository.seats(id,layout.create(em.getReference(Flight.class,id),selected,route.getPrice()));
        return true;
    }
}
