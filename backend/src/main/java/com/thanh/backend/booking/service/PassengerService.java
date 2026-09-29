package com.thanh.backend.booking.service;

import com.thanh.backend.auth.entity.User;
import com.thanh.backend.booking.dto.request.PassengerRequest;
import com.thanh.backend.booking.dto.response.PassengerResponse;
import com.thanh.backend.booking.entity.Passenger;
import com.thanh.backend.booking.entity.PassengerDocument;
import com.thanh.backend.booking.mapper.BookingMapper;
import com.thanh.backend.booking.repository.PassengerDocumentRepository;
import com.thanh.backend.booking.repository.PassengerRepository;
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
    private final BookingMapper bookingMapper;

    /**
     * Lấy danh sách hành khách đã lưu trong danh bạ của tài khoản hiện tại.
     */
    @Transactional(readOnly = true)
    public List<PassengerResponse> getMyPassengers(User user) {
        return passengerRepository.findByUserId(user.getId()).stream()
                .map(bookingMapper::toPassengerResponse)
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

        if (request.getIdentityType() != null && StringUtils.hasText(request.getIdentityNumber())) {
            PassengerDocument doc = PassengerDocument.builder()
                    .passenger(savedPassenger)
                    .identityType(request.getIdentityType())
                    .identityNumber(request.getIdentityNumber())
                    .identityExpiry(request.getIdentityExpiry())
                    .build();
            passengerDocumentRepository.save(doc);
        }

        return bookingMapper.toPassengerResponse(savedPassenger);
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
        return bookingMapper.toPassengerResponse(updatedPassenger);
    }

    /**
     * Xóa hành khách khỏi danh bạ cá nhân.
     */
    @Transactional
    public void deletePassenger(User user, Long passengerId) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new AppException(ErrorCode.PASSENGER_NOT_FOUND));

        if (!passenger.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        passengerRepository.delete(passenger);
    }
}
