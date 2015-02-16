package com.gentics.cailun.core.rest.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.cailun.core.repository.GlobalLanguageRepository;
import com.gentics.cailun.test.AbstractDBTest;

public class LanguageTest extends AbstractDBTest {

	@Autowired
	GlobalLanguageRepository languageRepository;

	@Test
	public void testCreation() {
		final String languageName = "klingon";
		Language lang = new Language(languageName);
		languageRepository.save(lang);
		lang = languageRepository.findOne(lang.getId());
		assertNotNull(lang);
		assertEquals(languageName, lang.getName());
	}
}
