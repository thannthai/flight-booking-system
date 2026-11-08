package com.thanh.backend.payment.service;
import com.thanh.backend.payment.config.DemoPaymentConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.backend.auth.dto.request.LoginRequest;
import com.thanh.backend.auth.enums.UserRole;
import com.thanh.backend.auth.repository.UserRepository;
import com.thanh.backend.auth.service.AuthService;
import com.thanh.backend.common.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class DemoExperienceService implements ApplicationRunner {
    private final DemoPaymentConfiguration config;
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final UserRepository users;
    private final ObjectMapper json;

    @Override public void run(ApplicationArguments args) {
        if (!config.isAccountsEnabled()) return;
        config.assertDemo();
        if (config.getAccountPassword().length() < 16) throw new IllegalStateException("Demo account password must have at least 16 characters");
        transactions.executeWithoutResult(tx -> {
            for (String alias : new String[]{"A"}) {
                db.update("""
                    INSERT INTO users(email,password_hash,full_name,phone,role,demo_alias)
                    VALUES (?,?,?,?, 'ROLE_USER',?) ON CONFLICT (demo_alias) DO NOTHING
                    """, "demo-"+alias.toLowerCase()+"@portfolio.invalid", encoder.encode(config.getAccountPassword()),
                        "Demo "+alias, alias.equals("A")?"0000000001":"0000000002", alias);
            }
        });
    }

    public Map<String,Object> info() {
        if (!config.isDemo()) return Map.of("enabled",false);
        config.assertDemo();
        var flights = db.queryForList("""
            SELECT DISTINCT ON (f.origin_iata_id,f.dest_iata_id)
              f.id, o.iata_code AS origin, d.iata_code AS destination,
              o.timezone AS origin_timezone, d.timezone AS destination_timezone,
              to_char(f.departure_time AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS"Z"') AS departure_time
            FROM flights f JOIN airports o ON o.id=f.origin_iata_id JOIN airports d ON d.id=f.dest_iata_id
            WHERE f.source='DEMO' AND f.status='SCHEDULED' AND f.departure_time>now()+interval '2 hours'
              AND EXISTS (SELECT 1 FROM flight_seats s WHERE s.flight_id=f.id AND s.status='AVAILABLE')
            ORDER BY f.origin_iata_id,f.dest_iata_id,f.departure_time LIMIT 12
            """);
        return Map.of("enabled",true,"accountsEnabled",config.isAccountsEnabled(),"flights",flights);
    }

    public com.thanh.backend.auth.dto.internal.AuthTokens login(String alias) {
        if (!config.isDemo() || !config.isAccountsEnabled() || !alias.equals("A")) throw new AppException(ErrorCode.FORBIDDEN);
        config.assertDemo();
        var emails = db.queryForList("SELECT email FROM users WHERE demo_alias=? AND role='ROLE_USER'",String.class,alias);
        if (emails.size()!=1) throw new AppException(ErrorCode.FORBIDDEN);
        var user=users.findByEmail(emails.getFirst()).orElseThrow();
        if (user.getRole()!=UserRole.ROLE_USER) throw new AppException(ErrorCode.FORBIDDEN);
        var request=json.convertValue(Map.of("email",user.getEmail(),"password",config.getAccountPassword()),LoginRequest.class);
        return auth.login(request);
    }
}
