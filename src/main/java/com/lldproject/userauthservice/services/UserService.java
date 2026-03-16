package com.lldproject.userauthservice.services;

import com.lldproject.userauthservice.models.User;
import com.lldproject.userauthservice.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
@Service
public class UserService {
    @Autowired
    private UserRepo userRepo;

    public User findUserById(Long id) {
        Optional<User> userOptional = userRepo.findById(id);

        if(userOptional.isPresent()) {
            return userOptional.get();
        }

        return null;
    }
}
