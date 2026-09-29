package com.thanh.backend.booking.service;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.booking.dto.request.AddOnRequest;
import com.thanh.backend.booking.dto.request.ContactInfoRequest;
import com.thanh.backend.booking.dto.request.CreateBookingRequest;
import com.thanh.backend.booking.dto.request.HoldSeatsRequest;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.request.SeatSelectionRequest;
import com.thanh.backend.booking.dto.response.AddOnResponse;
import com.thanh.backend.booking.dto.response.BookingResponse;
import com.thanh.backend.booking.dto.response.HoldSeatsResponse;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.dto.response.TicketResponse;
import com.thanh.backend.booking.entity.AddOnService;
import com.thanh.backend.booking.entity.Booking;
import com.thanh.backend.booking.entity.BookingLeg;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.PassengerDocument;
import com.thanh.backend.booking.entity.Ticket;
import com.thanh.backend.booking.enums.AddOnType;
import com.thanh.backend.booking.enums.BookingStatus;
import com.thanh.backend.booking.enums.LegDirection;
import com.thanh.backend.booking.enums.PassengerType;
import com.thanh.backend.booking.enums.TicketStatus;
import com.thanh.backend.booking.enums.TripType;
import com.thanh.backend.booking.mapper.BookingMapper;
import com.thanh.backend.booking.repository.AddOnServiceRepository;
import com.thanh.backend.booking.repository.BookingLegRepository;
import com.thanh.backend.booking.repository.BookingRepository;
import com.thanh.backend.booking.repository.PassengerDocumentRepository;
import com.thanh.backend.booking.repository.PassengerRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import com.thanh.backend.flight.entity.Flight;
import com.thanh.backend.flight.entity.FlightSeat;
import com.thanh.backend.flight.enums.FlightStatus;
import com.thanh.backend.flight.enums.SeatStatus;
import com.thanh.backend.flight.mapper.FlightMapper;
import com.thanh.backend.flight.repository.FlightRepository;
import com.thanh.backend.flight.repository.FlightSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String PNR_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final BigDecimal CHILD_FARE_RATE = new BigDecimal("0.75");
    private static final BigDecimal INFANT_FARE_RATE = new BigDecimal("0.10");

    @Value("${app.booking.hold-ttl-seconds:900}")
    private long holdTtlSeconds;

    private final BookingRepository bookingRepository;
    private final BookingLegRepository bookingLegRepository;
    private final PassengerRepository passengerRepository;
    private final PassengerDocumentRepository passengerDocumentRepository;
    private final TicketRepository ticketRepository;
    private final AddOnServiceRepository addOnServiceRepository;
    private final FlightRepository flightRepository;
    private final FlightSeatRepository flightSeatRepository;
    private final BookingMapper bookingMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * Giữ ghế trong 15 phút (Redis Pure SETNX Lock + Atomic DB Seat Locking).
     */
    @Transactional
    public HoldSeatsResponse holdSeats(User user, HoldSeatsRequest request) {
        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()
                || request.getSeatIds().stream().distinct().count() != request.getSeatIds().size()) {
            throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(holdTtlSeconds);

        List<Long> successfullyHeldSeats = new ArrayList<>();

        try {
            for (Long seatId : request.getSeatIds()) {
                FlightSeat seat = flightSeatRepository.findById(seatId)
                        .orElseThrow(() -> new AppException(ErrorCode.SEAT_NOT_AVAILABLE));

                if (seat.getFlight().getStatus() != FlightStatus.SCHEDULED && seat.getFlight().getStatus() != FlightStatus.DELAYED) {
                    throw new AppException(ErrorCode.FLIGHT_NOT_BOOKABLE);
                }

                boolean isAvailable = seat.getStatus() == SeatStatus.AVAILABLE
                        || (seat.getStatus() == SeatStatus.HELD
                        && seat.getHeldUntil() != null
                        && seat.getHeldUntil().isBefore(now));

                if (!isAvailable) {
                    throw new AppException(ErrorCode.SEAT_HELD);
                }

                String redisKey = "hold:seat:" + seatId;
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        redisKey,
                        user.getId().toString(),
                        holdTtlSeconds,
                        TimeUnit.SECONDS
                );

                if (!Boolean.TRUE.equals(acquired)) {
                    throw new AppException(ErrorCode.SEAT_HELD);
                }

                try {
                    seat.setStatus(SeatStatus.HELD);
                    seat.setHeldByUser(user);
                    seat.setHeldUntil(expiresAt);
                    flightSeatRepository.save(seat);
                } catch (Exception e) {
                    String currentLockOwner = redisTemplate.opsForValue().get(redisKey);
                    if (user.getId().toString().equals(currentLockOwner)) {
                        redisTemplate.delete(redisKey);
                    }
                    throw e;
                }

                successfullyHeldSeats.add(seatId);
            }
        } catch (Exception e) {
            for (Long heldSeatId : successfullyHeldSeats) {
                String redisKey = "hold:seat:" + heldSeatId;
                String currentOwner = redisTemplate.opsForValue().get(redisKey);
                if (user.getId().toString().equals(currentOwner)) {
                    redisTemplate.delete(redisKey);
                }
            }
            throw e;
        }

        return HoldSeatsResponse.builder()
                .heldSeats(successfullyHeldSeats)
                .expiresAt(expiresAt)
                .ttlSeconds(holdTtlSeconds)
                .build();
    }

    /**
     * Tạo mới đơn đặt vé (Sinh mã PNR 6 ký tự, lưu chặng bay, thông tin liên hệ, hành khách & vé nháp PENDING).
     */
    @Transactional
    public BookingResponse createBooking(User user, CreateBookingRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Chuẩn hóa danh sách seatSelections chống NPE
        List<SeatSelectionRequest> seatSelections = request.getSeatSelections() != null
                ? request.getSeatSelections()
                : List.of();

        // -------------------------------------------------------------
        // Business Validation 1: Kiểm tra loại chuyến bay (TripType) và số lượng flightIds
        // -------------------------------------------------------------
        if (request.getFlightIds() == null || request.getFlightIds().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
        }
        if (request.getFlightIds().stream().distinct().count() != request.getFlightIds().size()) {
            throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
        }
        if (request.getTripType() == TripType.ONE_WAY && request.getFlightIds().size() != 1) {
            throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
        }
        if (request.getTripType() == TripType.ROUND_TRIP && request.getFlightIds().size() != 2) {
            throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
        }

        // -------------------------------------------------------------
        // Business Validation 2: Kiểm tra thông tin người liên hệ nhận vé
        // -------------------------------------------------------------
        ContactInfoRequest contactInfo = request.getContactInfo();
        if (contactInfo == null
                || !StringUtils.hasText(contactInfo.getFullName())
                || !StringUtils.hasText(contactInfo.getEmail())
                || !StringUtils.hasText(contactInfo.getPhone())) {
            throw new AppException(ErrorCode.INVALID_CONTACT_INFO);
        }

        // -------------------------------------------------------------
        // Business Validation 3: Lấy danh sách chuyến bay (Chuyến đi & Chuyến về nếu khứ hồi)
        // -------------------------------------------------------------
        List<Flight> flights = new ArrayList<>();
        for (Long flightId : request.getFlightIds()) {
            Flight flight = flightRepository.findById(flightId)
                    .orElseThrow(() -> new AppException(ErrorCode.FLIGHT_NOT_FOUND));
            if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
                throw new AppException(ErrorCode.FLIGHT_NOT_BOOKABLE);
            }
            flights.add(flight);
        }

        if (request.getTripType() == TripType.ROUND_TRIP) {
            Flight outbound = flights.get(0);
            Flight returnFlight = flights.get(1);

            if (!returnFlight.getDepartureTime().isAfter(outbound.getDepartureTime())) {
                throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
            }

            if (outbound.getDest() == null || returnFlight.getOrigin() == null
                    || !outbound.getDest().getId().equals(returnFlight.getOrigin().getId())) {
                throw new AppException(ErrorCode.INVALID_TRIP_TYPE);
            }
        }

        // Lấy chuyến bay chiều đi đầu tiên để làm mốc tính độ tuổi hành khách tại ngày cất cánh
        Flight outboundFlight = flights.getFirst();

        // -------------------------------------------------------------
        // Business Validation 4: Validate độ tuổi (tại ngày cất cánh departureTime) và giấy tờ tùy thân
        // -------------------------------------------------------------
        List<PassengerRequest> passengerReqs = request.getPassengers();
        if (passengerReqs == null || passengerReqs.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PASSENGER_INDEX);
        }
        for (PassengerRequest passengerReq : passengerReqs) {
            validatePassengerAgeAtDeparture(passengerReq, outboundFlight);
            validatePassengerDocument(passengerReq);
        }

        // -------------------------------------------------------------
        // Business Validation 5: Validate INFANT và Người giám hộ ADULT (1 ADULT bế tối đa 1 INFANT)
        // -------------------------------------------------------------
        validateInfantsAndGuardians(passengerReqs);

        // -------------------------------------------------------------
        // Business Validation 5.1: Validate tính duy nhất của giấy tờ tùy thân & quy tắc INFANT guardian
        // -------------------------------------------------------------
        validatePassengerDocumentsUniqueness(passengerReqs);

        // -------------------------------------------------------------
        // Business Validation 5.2: Validate hành khách (ADULT/CHILD) chưa có vé active trên cùng chuyến bay
        // -------------------------------------------------------------
        validateNoDuplicateActiveBookingsForPassengers(passengerReqs, flights);

        // -------------------------------------------------------------
        // Business Validation 6: Validate danh sách ghế chọn (seatSelections)
        // -------------------------------------------------------------
        validateSeatSelections(seatSelections, flights, passengerReqs, user, now);

        // -------------------------------------------------------------
        // 1. Tính toán tổng số tiền (Fare Calculation theo Project Rules)
        // -------------------------------------------------------------
        BigDecimal baseAmount = calculateBaseAmount(seatSelections, flights, passengerReqs);
        BigDecimal taxAmount = baseAmount.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = baseAmount.add(taxAmount).subtract(discountAmount);

        // 2. Sinh mã đặt chỗ PNR 6 ký tự ngẫu nhiên duy nhất
        String pnr = generateUniquePnr();

        // 3. Lưu đơn Booking (status = PENDING) kèm contact_info
        Booking booking = Booking.builder()
                .pnr(pnr)
                .user(user)
                .contactName(contactInfo.getFullName())
                .contactEmail(contactInfo.getEmail())
                .contactPhone(contactInfo.getPhone())
                .tripType(request.getTripType())
                .baseAmount(baseAmount)
                .taxAmount(taxAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING)
                .voucherId(request.getVoucherId())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // 4. Lưu các chặng bay (BookingLeg)
        List<BookingLeg> savedLegs = new ArrayList<>();
        int legOrder = 1;
        for (Flight flight : flights) {
            LegDirection direction = (legOrder == 1) ? LegDirection.OUTBOUND : LegDirection.RETURN;

            BookingLeg leg = BookingLeg.builder()
                    .booking(savedBooking)
                    .flight(flight)
                    .legOrder(legOrder++)
                    .direction(direction)
                    .build();

            savedLegs.add(bookingLegRepository.save(leg));
        }

        // 5. Lưu thông tin Hành khách (Passenger) & Người giám hộ (guardian_id) -> Trả về Map<passengerIndex, Passenger>
        Map<Integer, Passenger> passengerMap = savePassengersAndDocuments(user, passengerReqs);

        // 6. Lưu Vé điện tử (Ticket) & AddOn Services Snapshot bằng passengerMap chính xác 100%
        List<Ticket> savedTickets = saveTicketsAndAddOns(savedBooking, savedLegs, passengerMap, passengerReqs, seatSelections);

        List<Passenger> savedPassengersList = new ArrayList<>(passengerMap.values());
        return buildBookingResponse(savedBooking, savedPassengersList, savedTickets);
    }

    /**
     * Validate độ tuổi của hành khách dựa trên ngày cất cánh của chuyến bay đầu tiên.
     */
    private void validatePassengerAgeAtDeparture(PassengerRequest passengerReq, Flight firstFlight) {
        if (passengerReq.getDateOfBirth() == null) {
            throw new AppException(ErrorCode.INVALID_PASSENGER_AGE);
        }

        LocalDate dob = passengerReq.getDateOfBirth();
        LocalDate departureDate = firstFlight.getDepartureTime().toLocalDate();
        int age = Period.between(dob, departureDate).getYears();

        PassengerType expectedType;
        if (age >= 12) {
            expectedType = PassengerType.ADULT;
        } else if (age >= 2) {
            expectedType = PassengerType.CHILD;
        } else {
            expectedType = PassengerType.INFANT;
        }

        if (passengerReq.getPassengerType() != expectedType) {
            throw new AppException(ErrorCode.INVALID_PASSENGER_AGE);
        }
    }

    /**
     * Validate thông tin giấy tờ tùy thân của hành khách.
     */
    private void validatePassengerDocument(PassengerRequest passengerReq) {
        boolean hasType = passengerReq.getIdentityType() != null;
        boolean hasNumber = StringUtils.hasText(passengerReq.getIdentityNumber());

        if (hasType && !hasNumber) {
            throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
        }
        if (!hasType && hasNumber) {
            throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
        }

        if (passengerReq.getPassengerType() == PassengerType.ADULT) {
            if (!hasType || !hasNumber) {
                throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
            }
        }
    }

    /**
     * Validate quy tắc Người giám hộ cho INFANT (Mỗi ADULT bế tối đa 1 INFANT).
     */
    private void validateInfantsAndGuardians(List<PassengerRequest> passengerReqs) {
        int adultCount = 0;
        int infantCount = 0;
        Set<Integer> assignedGuardians = new HashSet<>();

        for (int i = 0; i < passengerReqs.size(); i++) {
            PassengerRequest p = passengerReqs.get(i);
            if (p.getPassengerType() == PassengerType.ADULT) {
                adultCount++;
            } else if (p.getPassengerType() == PassengerType.INFANT) {
                infantCount++;

                Integer gIdx = p.getGuardianPassengerIndex();
                if (gIdx == null || gIdx < 0 || gIdx >= passengerReqs.size() || gIdx == i) {
                    throw new AppException(ErrorCode.INVALID_INFANT_GUARDIAN);
                }

                PassengerRequest guardianReq = passengerReqs.get(gIdx);
                if (guardianReq.getPassengerType() != PassengerType.ADULT) {
                    throw new AppException(ErrorCode.INVALID_INFANT_GUARDIAN);
                }

                if (assignedGuardians.contains(gIdx)) {
                    throw new AppException(ErrorCode.INVALID_INFANT_GUARDIAN);
                }
                assignedGuardians.add(gIdx);
            }
        }

        if (infantCount > 0 && adultCount == 0) {
            throw new AppException(ErrorCode.INVALID_INFANT_GUARDIAN);
        }
        if (infantCount > adultCount) {
            throw new AppException(ErrorCode.INVALID_INFANT_GUARDIAN);
        }
    }

    /**
     * Validate tính duy nhất của giấy tờ tùy thân và quy tắc giấy tờ cho INFANT.
     */
    private void validatePassengerDocumentsUniqueness(List<PassengerRequest> passengerReqs) {
        Set<String> adultChildDocSet = new HashSet<>();

        for (int i = 0; i < passengerReqs.size(); i++) {
            PassengerRequest req = passengerReqs.get(i);
            boolean hasDocument = req.getIdentityType() != null && StringUtils.hasText(req.getIdentityNumber());

            if (!hasDocument) {
                continue;
            }

            String docKey = req.getIdentityType().name() + "_" + req.getIdentityNumber().trim().toUpperCase();

            if (req.getPassengerType() == PassengerType.ADULT || req.getPassengerType() == PassengerType.CHILD) {
                if (!adultChildDocSet.add(docKey)) {
                    throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
                }
            } else if (req.getPassengerType() == PassengerType.INFANT) {
                PassengerRequest guardianReq = passengerReqs.get(req.getGuardianPassengerIndex());
                boolean guardianHasDoc = guardianReq.getIdentityType() != null && StringUtils.hasText(guardianReq.getIdentityNumber());

                if (!guardianHasDoc) {
                    throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
                }

                String guardianDocKey = guardianReq.getIdentityType().name() + "_" + guardianReq.getIdentityNumber().trim().toUpperCase();
                if (!docKey.equals(guardianDocKey)) {
                    throw new AppException(ErrorCode.INVALID_PASSENGER_DOCUMENT);
                }
            }
        }
    }

    /**
     * Validate hành khách (ADULT và CHILD) chưa có vé ở trạng thái ACTIVE (PENDING/COMPLETED) trên cùng chuyến bay.
     * INFANT được bỏ qua vì dùng chung giấy tờ với Người giám hộ.
     */
    private void validateNoDuplicateActiveBookingsForPassengers(List<PassengerRequest> passengerReqs, List<Flight> flights) {
        List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.COMPLETED);

        for (PassengerRequest pReq : passengerReqs) {
            if (pReq.getPassengerType() == PassengerType.INFANT) {
                continue;
            }

            if (pReq.getIdentityType() != null && StringUtils.hasText(pReq.getIdentityNumber())) {
                for (Flight flight : flights) {
                    boolean exists = ticketRepository.existsActiveBookingForPassenger(
                            flight.getId(),
                            pReq.getIdentityType(),
                            pReq.getIdentityNumber(),
                            activeStatuses
                    );

                    if (exists) {
                        throw new AppException(ErrorCode.PASSENGER_ALREADY_BOOKED_ON_FLIGHT);
                    }
                }
            }
        }
    }

    /**
     * Validate danh sách ghế được chọn (seatSelections) theo từng chặng bay.
     */
    private void validateSeatSelections(List<SeatSelectionRequest> seatSelections, List<Flight> flights,
                                       List<PassengerRequest> passengerReqs, User user, OffsetDateTime now) {
        int seatableCount = 0;
        for (PassengerRequest p : passengerReqs) {
            if (p.getPassengerType() == PassengerType.ADULT || p.getPassengerType() == PassengerType.CHILD) {
                seatableCount++;
            }
        }

        Set<Long> validFlightIds = new HashSet<>();
        for (Flight flight : flights) {
            validFlightIds.add(flight.getId());
            long flightSeatCount = seatSelections.stream()
                    .filter(s -> s.getFlightId().equals(flight.getId()))
                    .count();

            if (flightSeatCount != seatableCount) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }
        }

        Set<String> processedPassengerFlightPairs = new HashSet<>();
        Set<Long> processedSeatIds = new HashSet<>();

        for (SeatSelectionRequest seatReq : seatSelections) {
            if (seatReq.getPassengerIndex() == null || seatReq.getPassengerIndex() < 0 || seatReq.getPassengerIndex() >= passengerReqs.size()) {
                throw new AppException(ErrorCode.INVALID_PASSENGER_INDEX);
            }
            if (seatReq.getFlightSeatId() == null || seatReq.getFlightId() == null) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }

            // Chặn ghế chọn thuộc chuyến bay không nằm trong đơn hàng
            if (!validFlightIds.contains(seatReq.getFlightId())) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }

            // Chặn 2 hành khách chọn cùng một ghế
            if (!processedSeatIds.add(seatReq.getFlightSeatId())) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }

            PassengerRequest passengerReq = passengerReqs.get(seatReq.getPassengerIndex());
            if (passengerReq.getPassengerType() == PassengerType.INFANT) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }

            String pairKey = seatReq.getPassengerIndex() + "-" + seatReq.getFlightId();
            if (processedPassengerFlightPairs.contains(pairKey)) {
                throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
            }
            processedPassengerFlightPairs.add(pairKey);

            FlightSeat seat = flightSeatRepository.findById(seatReq.getFlightSeatId())
                    .orElseThrow(() -> new AppException(ErrorCode.SEAT_NOT_AVAILABLE));

            if (!seat.getFlight().getId().equals(seatReq.getFlightId())) {
                throw new AppException(ErrorCode.SEAT_NOT_AVAILABLE);
            }

            boolean isHeldByThisUserInDb = seat.getStatus() == SeatStatus.HELD
                    && seat.getHeldByUser() != null
                    && user.getId().equals(seat.getHeldByUser().getId())
                    && seat.getHeldUntil() != null
                    && seat.getHeldUntil().isAfter(now);

            if (!isHeldByThisUserInDb) {
                throw new AppException(ErrorCode.SEAT_NOT_AVAILABLE);
            }

            String lockOwner = redisTemplate.opsForValue().get("hold:seat:" + seat.getId());
            if (!user.getId().toString().equals(lockOwner)) {
                throw new AppException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
        }
    }

    /**
     * Tính toán tổng giá baseAmount của đơn đặt vé.
     */
    private BigDecimal calculateBaseAmount(List<SeatSelectionRequest> seatSelections, List<Flight> flights, List<PassengerRequest> passengerReqs) {
        BigDecimal totalSeatPrice = BigDecimal.ZERO;

        for (SeatSelectionRequest seatReq : seatSelections) {
            FlightSeat seat = flightSeatRepository.findById(seatReq.getFlightSeatId()).orElseThrow();
            PassengerRequest pReq = passengerReqs.get(seatReq.getPassengerIndex());

            BigDecimal seatPrice = seat.getPrice();
            if (pReq.getPassengerType() == PassengerType.CHILD) {
                seatPrice = seatPrice.multiply(CHILD_FARE_RATE).setScale(2, RoundingMode.HALF_UP);
            }
            totalSeatPrice = totalSeatPrice.add(seatPrice);
        }

        for (PassengerRequest pReq : passengerReqs) {
            if (pReq.getPassengerType() == PassengerType.INFANT) {
                for (Flight flight : flights) {
                    BigDecimal infantPrice = flight.getBasePrice().multiply(INFANT_FARE_RATE).setScale(2, RoundingMode.HALF_UP);
                    totalSeatPrice = totalSeatPrice.add(infantPrice);
                }
            }
        }

        BigDecimal totalAddOnPrice = BigDecimal.ZERO;
        for (SeatSelectionRequest seatReq : seatSelections) {
            if (seatReq.getAddOns() != null) {
                for (AddOnRequest addOnReq : seatReq.getAddOns()) {
                    if (addOnReq.getServiceType() == null || (addOnReq.getQuantity() != null && addOnReq.getQuantity() < 1)) {
                        throw new AppException(ErrorCode.INVALID_SEAT_SELECTION);
                    }
                    int qty = addOnReq.getQuantity() != null ? addOnReq.getQuantity() : 1;
                    BigDecimal unitPrice = lookupServerSideAddOnPrice(addOnReq.getServiceType());
                    totalAddOnPrice = totalAddOnPrice.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
                }
            }
        }

        return totalSeatPrice.add(totalAddOnPrice);
    }

    /**
     * Lưu thông tin Hành khách và Người giám hộ vào DB (Trả về Map<passengerIndex, Passenger>).
     */
    private Map<Integer, Passenger> savePassengersAndDocuments(User user, List<PassengerRequest> passengerReqs) {
        Map<Integer, Passenger> passengerIndexMap = new HashMap<>();

        // Phase 1: Lưu các hành khách ADULT và CHILD trước
        for (int i = 0; i < passengerReqs.size(); i++) {
            PassengerRequest req = passengerReqs.get(i);
            if (req.getPassengerType() != PassengerType.INFANT) {
                Passenger p = Passenger.builder()
                        .user(user)
                        .fullName(req.getFullName())
                        .dateOfBirth(req.getDateOfBirth())
                        .gender(req.getGender())
                        .nationality(req.getNationality())
                        .build();

                Passenger saved = passengerRepository.save(p);
                passengerIndexMap.put(i, saved);

                if (req.getIdentityType() != null && StringUtils.hasText(req.getIdentityNumber())) {
                    PassengerDocument doc = PassengerDocument.builder()
                            .passenger(saved)
                            .identityType(req.getIdentityType())
                            .identityNumber(req.getIdentityNumber())
                            .identityExpiry(req.getIdentityExpiry())
                            .build();
                    passengerDocumentRepository.save(doc);
                }
            }
        }

        // Phase 2: Lưu các em bé INFANT và liên kết với Passenger ADULT làm người giám hộ
        for (int i = 0; i < passengerReqs.size(); i++) {
            PassengerRequest req = passengerReqs.get(i);
            if (req.getPassengerType() == PassengerType.INFANT) {
                Passenger guardian = passengerIndexMap.get(req.getGuardianPassengerIndex());

                Passenger infant = Passenger.builder()
                        .user(user)
                        .fullName(req.getFullName())
                        .dateOfBirth(req.getDateOfBirth())
                        .gender(req.getGender())
                        .nationality(req.getNationality())
                        .guardian(guardian)
                        .build();

                Passenger savedInfant = passengerRepository.save(infant);
                passengerIndexMap.put(i, savedInfant);

                if (req.getIdentityType() != null && StringUtils.hasText(req.getIdentityNumber())) {
                    PassengerDocument doc = PassengerDocument.builder()
                            .passenger(savedInfant)
                            .identityType(req.getIdentityType())
                            .identityNumber(req.getIdentityNumber())
                            .identityExpiry(req.getIdentityExpiry())
                            .build();
                    passengerDocumentRepository.save(doc);
                }
            }
        }

        return passengerIndexMap;
    }

    /**
     * Lưu Vé điện tử (Ticket) và AddOn Services Snapshot bằng passengerIndexMap chính xác 100%.
     */
    private List<Ticket> saveTicketsAndAddOns(Booking savedBooking, List<BookingLeg> savedLegs,
                                             Map<Integer, Passenger> passengerMap, List<PassengerRequest> passengerReqs,
                                             List<SeatSelectionRequest> seatSelections) {
        List<Ticket> savedTickets = new ArrayList<>();

        for (BookingLeg leg : savedLegs) {
            Long flightId = leg.getFlight().getId();

            for (int i = 0; i < passengerReqs.size(); i++) {
                PassengerRequest pReq = passengerReqs.get(i);
                Passenger passenger = passengerMap.get(i);

                if (passenger == null) {
                    throw new AppException(ErrorCode.INVALID_PASSENGER_INDEX);
                }

                if (pReq.getPassengerType() == PassengerType.INFANT) {
                    BigDecimal infantPrice = leg.getFlight().getBasePrice().multiply(INFANT_FARE_RATE).setScale(2, RoundingMode.HALF_UP);

                    Ticket ticket = Ticket.builder()
                            .booking(savedBooking)
                            .bookingLeg(leg)
                            .passenger(passenger)
                            .flightSeat(null)
                            .passengerType(PassengerType.INFANT)
                            .price(infantPrice)
                            .status(TicketStatus.PENDING)
                            .build();

                    savedTickets.add(ticketRepository.save(ticket));
                } else {
                    int finalI = i;
                    SeatSelectionRequest seatReq = seatSelections.stream()
                            .filter(s -> s.getFlightId().equals(flightId) && s.getPassengerIndex() == finalI)
                            .findFirst()
                            .orElseThrow(() -> new AppException(ErrorCode.INVALID_SEAT_SELECTION));

                    FlightSeat seat = flightSeatRepository.findById(seatReq.getFlightSeatId()).orElseThrow();
                    seat.setStatus(SeatStatus.BOOKED);
                    seat.setHeldUntil(null);
                    seat.setHeldByUser(null);
                    flightSeatRepository.save(seat);

                    BigDecimal seatPrice = seat.getPrice();
                    if (pReq.getPassengerType() == PassengerType.CHILD) {
                        seatPrice = seatPrice.multiply(CHILD_FARE_RATE).setScale(2, RoundingMode.HALF_UP);
                    }

                    Ticket ticket = Ticket.builder()
                            .booking(savedBooking)
                            .bookingLeg(leg)
                            .passenger(passenger)
                            .flightSeat(seat)
                            .passengerType(pReq.getPassengerType())
                            .price(seatPrice)
                            .status(TicketStatus.PENDING)
                            .build();

                    Ticket savedTicket = ticketRepository.save(ticket);
                    savedTickets.add(savedTicket);

                    if (seatReq.getAddOns() != null) {
                        for (AddOnRequest addOnReq : seatReq.getAddOns()) {
                            int qty = (addOnReq.getQuantity() != null && addOnReq.getQuantity() > 0) ? addOnReq.getQuantity() : 1;
                            BigDecimal unitPrice = lookupServerSideAddOnPrice(addOnReq.getServiceType());
                            String description = lookupServerSideAddOnDescription(addOnReq.getServiceType());

                            AddOnService addOn = AddOnService.builder()
                                    .ticket(savedTicket)
                                    .serviceType(addOnReq.getServiceType())
                                    .description(description)
                                    .price(unitPrice)
                                    .quantity(qty)
                                    .build();
                            addOnServiceRepository.save(addOn);
                        }
                    }
                }
            }
        }

        return savedTickets;
    }

    /**
     * Tra cứu giá Server-side của AddOn (Đảm bảo an toàn 100%, Client không thể tự ý sửa giá).
     */
    private BigDecimal lookupServerSideAddOnPrice(AddOnType serviceType) {
        return switch (serviceType) {
            case BAGGAGE -> new BigDecimal("200000.00");  // 200,000 VNĐ cho 1 gói 20kg
            case MEAL -> new BigDecimal("80000.00");      // 80,000 VNĐ cho 1 suất ăn nóng
            case INSURANCE -> new BigDecimal("50000.00"); // 50,000 VNĐ cho bảo hiểm du lịch
        };
    }

    /**
     * Mô tả dịch vụ theo quy ước của hệ thống.
     * Quy ước: BAGGAGE đại diện cho 1 gói ký gửi 20kg per quantity.
     */
    private String lookupServerSideAddOnDescription(AddOnType serviceType) {
        return switch (serviceType) {
            case BAGGAGE -> "Hành lý ký gửi 20kg";
            case MEAL -> "Suất ăn nóng trên máy bay";
            case INSURANCE -> "Bảo hiểm du lịch hoãn chuyến";
        };
    }

    /**
     * Xem thông tin chi tiết một đơn đặt vé theo ID.
     */
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(User user, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getUser().getId().equals(user.getId()) && user.getRole() != UserRole.ROLE_ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED_BOOKING_ACCESS);
        }

        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        List<Passenger> passengers = tickets.stream()
                .map(Ticket::getPassenger)
                .distinct()
                .toList();

        return buildBookingResponse(booking, passengers, tickets);
    }

    /**
     * Lấy danh sách lịch sử đặt vé của người dùng hiện tại (kèm phân trang).
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(User user, Pageable pageable) {
        return bookingRepository.findByUserId(user.getId(), pageable)
                .map(booking -> {
                    List<Ticket> tickets = ticketRepository.findByBookingId(booking.getId());
                    List<Passenger> passengers = tickets.stream()
                            .map(Ticket::getPassenger)
                            .distinct()
                            .toList();
                    return buildBookingResponse(booking, passengers, tickets);
                });
    }

    /**
     * Helper lắp ráp BookingResponse DTO chi tiết sử dụng BookingMapper (MapStruct).
     */
    private BookingResponse buildBookingResponse(Booking booking, List<Passenger> passengers, List<Ticket> tickets) {
        List<PassengerResponse> passengerResponses = passengers.stream()
                .map(bookingMapper::toPassengerResponse)
                .toList();

        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        List<AddOnService> allAddOns = ticketIds.isEmpty()
                ? List.of()
                : addOnServiceRepository.findByTicketIdIn(ticketIds);

        Map<Long, List<AddOnService>> addOnsByTicketId = allAddOns.stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getTicket().getId()));

        List<TicketResponse> ticketResponses = tickets.stream().map(ticket -> {
            List<AddOnResponse> addOns = addOnsByTicketId.getOrDefault(ticket.getId(), List.of()).stream()
                    .map(bookingMapper::toAddOnResponse)
                    .toList();

            return bookingMapper.toTicketResponse(ticket, addOns);
        }).toList();

        return bookingMapper.toResponse(booking, passengerResponses, ticketResponses);
    }

    /**
     * Helper sinh mã PNR 6 ký tự in hoa ngẫu nhiên duy nhất (VD: AB123X).
     */
    private String generateUniquePnr() {
        SecureRandom random = new SecureRandom();
        String pnr;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(PNR_CHARACTERS.charAt(random.nextInt(PNR_CHARACTERS.length())));
            }
            pnr = sb.toString();
        } while (bookingRepository.existsByPnr(pnr));
        return pnr;
    }
}
