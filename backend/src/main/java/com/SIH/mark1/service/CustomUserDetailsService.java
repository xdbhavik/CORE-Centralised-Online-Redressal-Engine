package com.SIH.mark1.service;

import com.SIH.mark1.model.User;
import com.SIH.mark1.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // We use mobile as username in this app — also accept email if needed
        User user = userRepository.findByMobile(username)
                .orElseGet(() -> userRepository.findByEmail(username).orElse(null));
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        Collection<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new org.springframework.security.core.userdetails.User(user.getMobile(), user.getPasswordHash(), authorities);
    }
}
