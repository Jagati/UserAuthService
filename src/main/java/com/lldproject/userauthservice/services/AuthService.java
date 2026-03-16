package com.lldproject.userauthservice.services;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lldproject.userauthservice.client.KafkaClient;
import com.lldproject.userauthservice.dtos.EmailDto;
import com.lldproject.userauthservice.exceptions.*;
import com.lldproject.userauthservice.models.Role;
import com.lldproject.userauthservice.models.Session;
import com.lldproject.userauthservice.models.State;
import com.lldproject.userauthservice.models.User;
import com.lldproject.userauthservice.repos.RoleRepo;
import com.lldproject.userauthservice.repos.SessionRepo;
import com.lldproject.userauthservice.repos.UserRepo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import org.antlr.v4.runtime.misc.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

@Service
public class AuthService implements IAuthService {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private RoleRepo roleRepo;
    @Autowired
    private SessionRepo sessionRepo;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private SecretKey secretKey;

    @Autowired
    private KafkaClient kafkaClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public User signup(String name, String email, String password) {
        Optional<User> optionalUser = userRepo.findByEmail(email);
        if (optionalUser.isPresent()) {
            throw new UserAlreadyExistsException("Please try a different email id.");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(bCryptPasswordEncoder.encode(password));
        user.setState(State.ACTIVE);
        user.setCreatedAt(new Date());
        Role role;
        Optional<Role> optionalRole = roleRepo.findByValue("NON-ADMIN");
        if (optionalRole.isPresent()) {
            role = optionalRole.get();
        } else {
            role = new Role();
            role.setValue("NON-ADMIN");
            role.setState(State.ACTIVE);
            role.setCreatedAt(new Date());
            roleRepo.save(role);
        }
        List<Role> roles = new ArrayList<>();
        roles.add(role);
        user.setRoles(roles);
        //Putting a message into Kafka/Message Queue

        EmailDto emailDto = new EmailDto();
        emailDto.setSubject("Welcome to Scaler");
        emailDto.setBody("Have a good learning experience");
        emailDto.setTo(email);
        emailDto.setFrom("anuragonhiring@gmail.com");

        try {
            kafkaClient.sendMessage("signup",
                    objectMapper.writeValueAsString(emailDto));

            return userRepo.save(user);
        }catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    public Pair<User, String> login(String email, String password) {
        Optional<User> optionalUser = userRepo.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new UserNotRegisteredException("Please signup first.");
        }
        User user = optionalUser.get();
        if (!bCryptPasswordEncoder.matches(password, user.getPassword())) {
            throw new PasswordMismatchException("Please enter correct password");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        List<String> roles = new ArrayList<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getValue());
        }
        claims.put("access", roles);
        Long currentTime = System.currentTimeMillis();
        claims.put("iat", currentTime);
        claims.put("exp", currentTime + 100000);
        claims.put("issuer", "MyCompany");
        MacAlgorithm algorithm = Jwts.SIG.HS256;
        SecretKey secretKey = algorithm.key().build();
        String token = Jwts.builder().claims(claims).signWith(secretKey).compact();
        Session session= new Session();
        session.setToken(token);
        session.setUser(user);
        session.setState(State.ACTIVE);
        session.setCreatedAt(new Date());
        sessionRepo.save(session);

        return new Pair<>(user, token);
    }

    @Override
    public void validateToken(String token) {
        Optional<Session> optionalSession = sessionRepo.findByToken(token);

        if (optionalSession.isEmpty()) {
            throw new InvalidTokenException("Please login !!!");
        }

        //check for expiry
        Session session = optionalSession.get();

        JwtParser jwtParser = Jwts.parser().verifyWith(secretKey).build();
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        Long expiry = (Long)claims.get("exp");
        Long currentTime = System.currentTimeMillis();
        System.out.println("expiry "+expiry);
        System.out.println("current Time "+currentTime);

        if(currentTime > expiry) {
            session.setState(State.INACTIVE);
            session.setUpdatedAt(new Date());
            sessionRepo.save(session);
            throw new TokenExpiredException("Please login again, token has expired");
        }
    }
}

