package com.Result_Analysis.Result_Analysis;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")

public class AuthController{
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository ){
        this.userRepository = userRepository;
    }

    @PostMapping("/register")

    public String register(@RequestBody User user){
        if(userRepository.findByUserId(user.getUserId()).isPresent()){
            return "User ID already exists";
        }

        userRepository.save(user);
        
        return "Registration successful";
    }
    
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user, HttpServletRequest request){
        Optional<User> existingUser = userRepository.findByUserId(user.getUserId());
        if(existingUser.isPresent() && existingUser.get().getPassword().equals(user.getPassword())){
            // Store authenticated identity server-side (session) — browser will receive JSESSIONID, not trusted uploadedBy
            HttpSession session = request.getSession(true);
            session.setAttribute("AUTH_USER_ID", existingUser.get().getUserId());
            session.setAttribute("AUTH_USER_NAME", existingUser.get().getName());
            return ResponseEntity.ok("Login Successful");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Cannot find your account");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null){
            Object uid = session.getAttribute("AUTH_USER_ID");
            if(uid != null) return ResponseEntity.ok(Map.of("authenticatedUser", uid.toString()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Not authenticated"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("message","Logged out"));
    }
}
