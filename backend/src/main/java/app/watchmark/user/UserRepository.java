package app.watchmark.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** {@code email} must already be normalised with {@link EmailAddresses#normalize}. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
