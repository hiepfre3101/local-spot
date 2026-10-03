package com.localspot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Vai trò RBAC — seed ở V2__seed_rbac.sql. Kế thừa = seed đủ permission cho mỗi role (database.md §3.1). */
@Entity
@Table(name = "roles")
public class Role {

    public static final String USER = "USER";
    public static final String OWNER = "OWNER";
    public static final String MODERATOR = "MODERATOR";
    public static final String ADMIN = "ADMIN";

    /** Tập role cố định (openapi {@code RoleName}); role mới cần migration seed + cập nhật hằng số này. */
    public static final Set<String> NAMES = Set.of(USER, OWNER, MODERATOR, ADMIN);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToMany
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
