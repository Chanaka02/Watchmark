package app.watchmark.tracking;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurable tracking retention rules (see docs/tracking-rules.md, R3 and R12).
 *
 * @param undoWindow how long after a tracking operation it may still be undone
 * @param softDeleteRetention how long an undone or unwatched watch row is kept
 *     before the purge job removes it permanently; must not be shorter than the
 *     undo window, otherwise an undoable operation could lose its rows
 * @param purgeEnabled whether the scheduled purge may permanently delete
 *     soft-deleted rows at all; when false they are kept indefinitely
 */
@Validated
@ConfigurationProperties("watchmark.tracking")
public record TrackingProperties(@NotNull Duration undoWindow, @NotNull Duration softDeleteRetention,
		boolean purgeEnabled) {

	public TrackingProperties {
		if (undoWindow != null && (undoWindow.isZero() || undoWindow.isNegative())) {
			throw new IllegalArgumentException("watchmark.tracking.undo-window must be positive");
		}
		if (undoWindow != null && softDeleteRetention != null && softDeleteRetention.compareTo(undoWindow) < 0) {
			throw new IllegalArgumentException(
					"watchmark.tracking.soft-delete-retention must be at least watchmark.tracking.undo-window");
		}
	}

}
