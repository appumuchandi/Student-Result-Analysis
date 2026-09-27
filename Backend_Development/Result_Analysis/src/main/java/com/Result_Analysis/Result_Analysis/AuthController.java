package com.Result_Analysis.Result_Analysis;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")

public class AuthController{
    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final StudentRepository studentRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository, PasswordResetOtpRepository otpRepository, StudentRepository studentRepository){
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.studentRepository = studentRepository;
    }

    @PostMapping("/register")

    public String register(@RequestBody User user){
        if(userRepository.findByUserId(user.getUserId()).isPresent()){
            return "User ID already exists";
        }
        // Hash password with BCrypt
        if(user.getPassword()!=null) user.setPassword(encoder.encode(user.getPassword()));
        if(user.getRole()==null || user.getRole().trim().isEmpty()) user.setRole("ADMIN");
        user.setRole(user.getRole().toUpperCase());
        if(user.getRole().equals("STUDENT") && user.getMustChangePassword()==null) user.setMustChangePassword(false);
        userRepository.save(user);
        
        return "Registration successful";
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user, HttpServletRequest request){
        Optional<User> existingUser = userRepository.findByUserId(user.getUserId());
        if(existingUser.isPresent()){
            User u = existingUser.get();
            String stored = u.getPassword();
            String supplied = user.getPassword();
            boolean matches = false;
            if(stored!=null && stored.startsWith("$2a$")){
                matches = encoder.matches(supplied, stored);
            } else {
                matches = stored!=null && stored.equals(supplied);
                // Migrate plaintext to BCrypt on successful login
                if(matches){
                    u.setPassword(encoder.encode(supplied));
                    // Ensure role is set
                    if(u.getRole()==null) { u.setRole("ADMIN"); }
                    userRepository.save(u);
                    stored = u.getPassword();
                }
            }
            if(matches){
                HttpSession session = request.getSession(true);
                session.setAttribute("AUTH_USER_ID", u.getUserId());
                session.setAttribute("AUTH_USER_NAME", u.getName());
                session.setAttribute("AUTH_USER_ROLE", u.getRole());
                // Return role and mustChangePassword for frontend
                return ResponseEntity.ok(Map.of(
                    "message","Login Successful",
                    "userId", u.getUserId(),
                    "role", u.getRole()==null?"ADMIN":u.getRole(),
                    "mustChangePassword", u.getMustChangePassword()!=null && u.getMustChangePassword()
                ));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Cannot find your account"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null){
            Object uid = session.getAttribute("AUTH_USER_ID");
            Object role = session.getAttribute("AUTH_USER_ROLE");
            Object name = session.getAttribute("AUTH_USER_NAME");
            if(uid != null){
                String roleStr = role!=null?role.toString():"ADMIN";
                // Fetch latest mustChangePassword from DB
                Optional<User> opt = userRepository.findByUserId(uid.toString());
                boolean must = opt.isPresent() && Boolean.TRUE.equals(opt.get().getMustChangePassword());
                return ResponseEntity.ok(Map.of(
                    "authenticatedUser", uid.toString(),
                    "role", roleStr,
                    "name", name!=null?name.toString():"",
                    "mustChangePassword", must
                ));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Not authenticated"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("message","Logged out"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String,String> body, HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Not authenticated"));
        Object uid = session.getAttribute("AUTH_USER_ID");
        if(uid==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Not authenticated"));
        String userId = uid.toString();
        Optional<User> opt = userRepository.findByUserId(userId);
        if(opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","User not found"));
        User u = opt.get();
        String current = body.get("currentPassword");
        String newPass = body.get("newPassword");
        String confirm = body.get("confirmPassword");
        if(current==null || newPass==null || confirm==null) return ResponseEntity.badRequest().body(Map.of("message","All fields required"));
        // Verify current password
        boolean matches = false;
        String stored = u.getPassword();
        if(stored!=null && stored.startsWith("$2a$")) matches = encoder.matches(current, stored);
        else matches = stored!=null && stored.equals(current);
        if(!matches) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message","Current password incorrect"));
        if(!newPass.equals(confirm)) return ResponseEntity.badRequest().body(Map.of("message","New passwords do not match"));
        if(newPass.length()<4) return ResponseEntity.badRequest().body(Map.of("message","New password too short"));
        u.setPassword(encoder.encode(newPass));
        u.setMustChangePassword(false);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message","Password changed successfully"));
    }



    // Forgot password flow
    @PostMapping("/forgot-password/request")
    public ResponseEntity<?> forgotRequest(@RequestBody Map<String,String> body){
        String userId = body.get("userId");
        if(userId==null || userId.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","User ID required"));
        userId = userId.trim();
        Optional<User> opt = userRepository.findByUserId(userId);
        // Do not reveal whether user exists
        String genericMsg = "If the account exists, an OTP has been sent to the registered contact.";
        if(opt.isEmpty()){
            return ResponseEntity.ok(Map.of("message", genericMsg));
        }
        User u = opt.get();
        // Check rate limiting: max 3 requests per 10 minutes
        java.time.LocalDateTime tenMinAgo = java.time.LocalDateTime.now().minusMinutes(10);
        var recent = otpRepository.findByUserIdAndCreatedAtAfter(userId, tenMinAgo);
        if(recent.size() >= 3){
            return ResponseEntity.status(429).body(Map.of("message","Too many OTP requests. Please try after 10 minutes."));
        }
        // Check if user has verified contact (phone or email)
        String contact = null;
        String maskedContact = "registered contact";
        if(u.getPhone()!=null && !u.getPhone().trim().isEmpty()){
            contact = u.getPhone();
            maskedContact = maskPhone(contact);
        } else {
            // Try Student contact
            var studentOpt = studentRepository.findByUsnIgnoreCase(userId);
            if(studentOpt.isPresent()){
                Student s = studentOpt.get();
                if(s.getPhoneNumber()!=null && !s.getPhoneNumber().trim().isEmpty()){
                    contact = s.getPhoneNumber();
                    maskedContact = maskPhone(contact);
                } else if(s.getEmail()!=null && !s.getEmail().trim().isEmpty()){
                    contact = s.getEmail();
                    maskedContact = maskEmail(contact);
                }
            }
            if(contact==null && u.getPhone()!=null && !u.getPhone().trim().isEmpty()){
                contact = u.getPhone();
                maskedContact = maskPhone(contact);
            }
        }
        if(contact==null){
            return ResponseEntity.ok(Map.of("message", genericMsg));
        }
        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        PasswordResetOtp entity = new PasswordResetOtp();
        entity.setUserId(userId);
        entity.setOtpHash(encoder.encode(otp));
        entity.setExpiry(java.time.LocalDateTime.now().plusMinutes(5));
        entity.setUsed(false);
        entity.setVerified(false);
        entity.setAttempts(0);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        otpRepository.save(entity);
        // In production, send OTP via SMS/email provider here
        // For now, do not expose OTP in response or logs
        return ResponseEntity.ok(Map.of("message", genericMsg, "maskedContact", maskedContact));
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<?> forgotVerify(@RequestBody Map<String,String> body){
        String userId = body.get("userId");
        String otp = body.get("otp");
        if(userId==null || otp==null) return ResponseEntity.badRequest().body(Map.of("message","User ID and OTP required"));
        userId = userId.trim(); otp = otp.trim();
        var opt = otpRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if(opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","Invalid or expired OTP"));
        PasswordResetOtp entity = opt.get();
        if(Boolean.TRUE.equals(entity.getUsed())) return ResponseEntity.badRequest().body(Map.of("message","OTP already used"));
        if(entity.getExpiry().isBefore(java.time.LocalDateTime.now())){
            return ResponseEntity.badRequest().body(Map.of("message","OTP expired"));
        }
        // Rate limiting for verify attempts
        if(entity.getAttempts()!=null && entity.getAttempts()>=5){
            return ResponseEntity.status(429).body(Map.of("message","Too many attempts. Request a new OTP."));
        }
        entity.setAttempts(entity.getAttempts()+1);
        otpRepository.save(entity);
        boolean matches = encoder.matches(otp, entity.getOtpHash());
        if(!matches){
            return ResponseEntity.badRequest().body(Map.of("message","Invalid OTP"));
        }
        entity.setVerified(true);
        otpRepository.save(entity);
        return ResponseEntity.ok(Map.of("message","OTP verified"));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> forgotReset(@RequestBody Map<String,String> body, HttpServletRequest request){
        String userId = body.get("userId");
        String newPass = body.get("newPassword");
        String confirm = body.get("confirmPassword");
        if(userId==null || newPass==null || confirm==null) return ResponseEntity.badRequest().body(Map.of("message","All fields required"));
        userId = userId.trim();
        if(!newPass.equals(confirm)) return ResponseEntity.badRequest().body(Map.of("message","Passwords do not match"));
        if(newPass.length()<4) return ResponseEntity.badRequest().body(Map.of("message","New password too short"));
        var opt = otpRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if(opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","No OTP verification found"));
        PasswordResetOtp entity = opt.get();
        if(!Boolean.TRUE.equals(entity.getVerified())) return ResponseEntity.badRequest().body(Map.of("message","OTP not verified"));
        if(Boolean.TRUE.equals(entity.getUsed())) return ResponseEntity.badRequest().body(Map.of("message","OTP already used"));
        if(entity.getExpiry().isBefore(java.time.LocalDateTime.now())) return ResponseEntity.badRequest().body(Map.of("message","OTP expired"));
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if(userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","User not found"));
        User u = userOpt.get();
        u.setPassword(encoder.encode(newPass));
        u.setMustChangePassword(false);
        userRepository.save(u);
        entity.setUsed(true);
        otpRepository.save(entity);
        // Invalidate existing sessions for this user (if any) — current session will be invalidated on next request, but we can clear this session if it matches
        HttpSession session = request.getSession(false);
        if(session!=null){
            Object uid = session.getAttribute("AUTH_USER_ID");
            if(uid!=null && uid.toString().equalsIgnoreCase(userId)){
                session.invalidate();
            }
        }
        return ResponseEntity.ok(Map.of("message","Password reset successful"));
    }

    private String maskPhone(String phone){
        if(phone==null || phone.length()<4) return "****";
        String digits = phone.replaceAll("[^0-9]","");
        if(digits.length()>=4) return "****"+digits.substring(digits.length()-4);
        return "****";
    }
    private String maskEmail(String email){
        if(email==null || !email.contains("@")) return "****";
        String[] parts = email.split("@");
        String local = parts[0];
        if(local.length()<=2) return local.charAt(0)+"****@"+parts[1];
        return local.charAt(0)+"****"+local.charAt(local.length()-1)+"@"+parts[1];
    }
}
