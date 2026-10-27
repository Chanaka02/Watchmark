package app.watchmark.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	User(String email, String displayName, String passwordHash, String timeZone, Instant now) {
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.timeZone = timeZone;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	void updateProfile(String displayName, String timeZone, Instant now) {
		if (displayName != null) {
			this.displayName = displayName;
		}
		if (timeZone != null) {
			this.timeZone = timeZone;
		}
		this.updatedAt = now;
	}

	void changePasswordHash(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

}
