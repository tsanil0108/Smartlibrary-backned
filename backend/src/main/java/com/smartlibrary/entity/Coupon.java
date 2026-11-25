package com.smartlibrary.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "coupons")
public class Coupon {

    @Id
    @Column(length = 40)
    private String code;

    private int percent;

    private int maxUses;

    private int used;

    private boolean active;

    private LocalDate expiresOn;
}