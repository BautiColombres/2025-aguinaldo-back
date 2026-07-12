package com.medibook.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
// Class-level batching of LAZY User proxies (up to 32 per select). Collapses the
// N+1 when a list of rows each lazily loads its patient/doctor User (e.g. the
// follow-up "due" panel — F2). Read-only optimization: results are unchanged, only
// the query count drops. Field-level @BatchSize is not allowed on @ManyToOne, so
// the batch hint lives on the target entity.
@org.hibernate.annotations.BatchSize(size = 32)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @NotNull
    @Column(name = "dni", nullable = false, unique = true)
    private Long dni;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String surname;

    private String phone;

    private LocalDate birthdate;

    private String gender;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private String role = "PATIENT";

    @Column(name = "score")
    private Double score;

    @Column(name = "must_reset_password", nullable = false)
    private boolean mustResetPassword = false;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MedicalHistory> medicalHistories = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    private DoctorProfile doctorProfile;

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public void setDoctorProfile(DoctorProfile doctorProfile) {
        this.doctorProfile = doctorProfile;
        if (doctorProfile != null) {
            // BBUG-M5: only wire the back-reference. The shared primary key is derived
            // from this user's id at persist time via @MapsId; forcing it here would copy
            // a possibly-null id onto the profile.
            doctorProfile.setUser(this);
        }
    }

}