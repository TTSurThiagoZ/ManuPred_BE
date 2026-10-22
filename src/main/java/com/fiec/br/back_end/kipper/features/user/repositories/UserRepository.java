package com.fiec.br.back_end.kipper.features.user.repositories;

import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmail(String email);

    Optional<Users> findByFirebaseUid(String firebaseUid);

    boolean existsByEmail(String email);

    List<Users> findAllByFcmToken(String fcmToken);
}