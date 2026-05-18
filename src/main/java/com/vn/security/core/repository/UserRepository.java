package com.vn.security.core.repository;

import com.vn.security.core.domain.User;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Email-based lookup methods (findOneByEmail*, USERS_BY_EMAIL_CACHE) đã được bỏ
 * khi starter loại bỏ email feature (bug #001).</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    String USERS_BY_LOGIN_CACHE = "usersByLogin";

    Optional<User> findOneByLogin(String login);

    @EntityGraph(attributePaths = "authorities")
    @Cacheable(cacheNames = USERS_BY_LOGIN_CACHE, unless = "#result == null")
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);

    @Query("select count(u) from User u join u.authorities a where a.name = :authorityName")
    long countByAuthorityName(@Param("authorityName") String authorityName);
}
