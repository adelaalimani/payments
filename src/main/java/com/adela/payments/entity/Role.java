package com.adela.payments.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "roles")
public class Role extends BaseEntity {

    private String name;
    @ManyToMany(mappedBy = "roles")
    private List<User> users;
}