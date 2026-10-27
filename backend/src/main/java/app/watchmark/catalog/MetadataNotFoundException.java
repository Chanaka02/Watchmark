package app.watchmark.catalog;

import org.springframework.http.HttpStatus;

import app.watchmark.common.ApiException;

public class MetadataNotFoundException extends ApiException {

	private MetadataNotFoundException(String type, String title, String detail) {
		super(HttpStatus.NOT_FOUND, type, title, detail);
	}

	public static MetadataNotFoundException show() {
		return new MetadataNotFoundException("show-not-found", "Show not found", "This show could not be found.");
	}

	public static MetadataNotFoundException season() {
		return new MetadataNotFoundException("season-not-found", "Season not found",
				"This season could not be found.");
	}

}
