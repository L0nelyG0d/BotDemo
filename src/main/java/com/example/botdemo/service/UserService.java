package com.example.botdemo.service;
import com.example.botdemo.User;
import com.example.botdemo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User saveUser(User user){
        return userRepository.save(user);
    }

    public User findByUsername(String username){
        return findByUsername(username);
    }
}
