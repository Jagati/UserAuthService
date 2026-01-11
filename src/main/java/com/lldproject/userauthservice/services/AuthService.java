package com.lldproject.userauthservice.services;
import com.lldproject.userauthservice.exceptions.PasswordMismatchException;
import com.lldproject.userauthservice.exceptions.UserAlreadyExistsException;
import com.lldproject.userauthservice.exceptions.UserNotRegisteredException;
import com.lldproject.userauthservice.models.Role;
import com.lldproject.userauthservice.models.State;
import com.lldproject.userauthservice.models.User;
import com.lldproject.userauthservice.repos.RoleRepo;
import com.lldproject.userauthservice.repos.UserRepo;
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
    private BCryptPasswordEncoder bCryptPasswordEncoder;

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
        return userRepo.save(user);
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
        return new Pair<>(user, token);
    }
}

