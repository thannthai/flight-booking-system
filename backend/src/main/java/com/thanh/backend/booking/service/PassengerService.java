package com.thanh.backend.booking.service;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.PassengerDocument;
import com.thanh.backend.booking.mapper.BookingMapper;
import com.thanh.backend.booking.repository.PassengerDocumentRepository;
import com.thanh.backend.booking.repository.PassengerRepository;
import com.thanh.backend.booking.repository.TicketRepository;
import com.thanh.backend.common.exception.AppException;
import com.thanh.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PassengerService {

    private final PassengerRepository passengerRepository;
    private final PassengerDocumentRepository passengerDocumentRepository;
    private final TicketRepository ticketRepository;
    private final BookingMapper bookingMapper;

    /**
     * Lấy danh sách hành khách đã lưu trong danh bạ của tài khoản hiện tại.
     */
    @Transactional(readOnly = true)
    public List<PassengerResponse> getMyPassengers(User user) {
        return passengerRepository.findByUserId(user.getId()).stream()
                .map(p -> {
                    PassengerResponse res = bookingMapper.toPassengerResponse(p);
                    List<PassengerDocument> docs = passengerDocumentRepository.findByPassengerId(p.getId());
                    if (!docs.isEmpty()) {
                        PassengerDocument doc = docs.get(0);
                        res.setIdentityType(doc.getIdentityType());
                        res.setIdentityNumber(doc.getIdentityNumber());
                        res.setIdentityExpiry(doc.getIdentityExpiry());
                    }
                    return res;
                })
                .toList();
    }

    /**
     * Thêm hành khách mới vào danh bạ cá nhân.
     */
    @Transactional
    public PassengerResponse createPassenger(User user, PassengerRequest request) {
        Passenger passenger = Passenger.builder()
                .user(user)
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .nationality(request.getNationality())
                .build();

        Passenger savedPassenger = passengerRepository.save(passenger);

        PassengerDocument savedDoc = null;
        if (request.getIdentityType() != null && StringUtils.hasText(request.getIdentityNumber())) {
            PassengerDocument doc = PassengerDocument.builder()
                    .passenger(savedPassenger)
                    .identityType(request.getIdentityType())
                    .identityNumber(request.getIdentityNumber())
                    .identityExpiry(request.getIdentityExpiry())
                    .build();
            savedDoc = passengerDocumentRepository.save(doc);
        }

        PassengerResponse res = bookingMapper.toPassengerResponse(savedPassenger);
        if (savedDoc != null) {
            res.setIdentityType(savedDoc.getIdentityType());
            res.setIdentityNumber(savedDoc.getIdentityNumber());
            res.setIdentityExpiry(savedDoc.getIdentityExpiry());
        }
        return res;
    }

    /**
     * Cập nhật thông tin hành khách trong danh bạ.
     */
    @Transactional
    public PassengerResponse updatePassenger(User user, Long passengerId, PassengerRequest request) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new AppException(ErrorCode.PASSENGER_NOT_FOUND));

        if (!passenger.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        passenger.setFullName(request.getFullName());
        passenger.setDateOfBirth(request.getDateOfBirth());
        passenger.setGender(request.getGender());
        passenger.setNationality(request.getNationality());

        Passenger updatedPassenger = passengerRepository.save(passenger);

        PassengerDocument updatedDoc = null;
        if (request.getIdentityType() != null && StringUtils.hasText(request.getIdentityNumber())) {
            List<PassengerDocument> docs = passengerDocumentRepository.findByPassengerId(passengerId);
            PassengerDocument doc;
            if (!docs.isEmpty()) {
                doc = docs.get(0);
                doc.setIdentityType(request.getIdentityType());
                doc.setIdentityNumber(request.getIdentityNumber());
                doc.setIdentityExpiry(request.getIdentityExpiry());
            } else {
                doc = PassengerDocument.builder()
                        .passenger(updatedPassenger)
                        .identityType(request.getIdentityType())
                        .identityNumber(request.getIdentityNumber())
                        .identityExpiry(request.getIdentityExpiry())
                        .build();
            }
            updatedDoc = passengerDocumentRepository.save(doc);
        }

        PassengerResponse res = bookingMapper.toPassengerResponse(updatedPassenger);
        if (updatedDoc != null) {
            res.setIdentityType(updatedDoc.getIdentityType());
            res.setIdentityNumber(updatedDoc.getIdentityNumber());
            res.setIdentityExpiry(updatedDoc.getIdentityExpiry());
        }
        return res;
    }

    /**
     * Xóa hành khách khỏi danh bạ cá nhân.
     */
    @Transactional
    public void deletePassenger(User user, Long passengerId) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new AppException(ErrorCode.PASSENGER_NOT_FOUND));

        if (passenger.getUser() == null || !passenger.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        boolean hasTickets = ticketRepository.existsByPassengerId(passengerId);
        if (hasTickets) {
            // Tách hành khách khỏi danh bạ cá nhân của user để không làm hỏng vé/đơn đặt chỗ trong quá khứ
            passenger.setUser(null);
            passengerRepository.save(passenger);
        } else {
            // Nếu là hành khách thuần danh bạ (chưa có vé), gỡ liên kết giám hộ nếu có và xóa vĩnh viễn
            List<Passenger> infants = passengerRepository.findByGuardianId(passengerId);
            for (Passenger infant : infants) {
                infant.setGuardian(null);
            }
            if (!infants.isEmpty()) {
                passengerRepository.saveAll(infants);
            }

            List<PassengerDocument> docs = passengerDocumentRepository.findByPassengerId(passengerId);
            passengerDocumentRepository.deleteAll(docs);

            passengerRepository.delete(passenger);
        }
    }
}
