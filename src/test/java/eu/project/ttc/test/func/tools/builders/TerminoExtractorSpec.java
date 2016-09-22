package eu.project.ttc.test.func.tools.builders;


import static eu.project.ttc.test.TermSuiteAssertions.assertThat;

import java.nio.file.Paths;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import eu.project.ttc.engines.desc.Lang;
import eu.project.ttc.models.TermIndex;
import eu.project.ttc.test.func.FunctionalTests;
import eu.project.ttc.tools.builders.Document;
import eu.project.ttc.tools.builders.TerminoExtractor;

public class TerminoExtractorSpec {
	
	Lang lang;
	List<Document> documents;
	Document document1;
	Document document2;
	
	
	@Before
	public void setup() {
		lang = Lang.FR;
		documents = Lists.newArrayList();
		document1 = new Document(lang, "url1", "L'énergie éolienne est l'énergie de demain.");
		documents.add(document1);
		document2 = new Document(lang, "url2", "Une éolienne produit de l'énergie.");
		documents.add(document2);
	}


	@Test
	public void fromPreprocessedJsonFiles() {
		String jsonDirPath = Paths.get(FunctionalTests.CORPUS2_PATH.toString(), "json").toString();
		TermIndex termIndex = TerminoExtractor
				.fromPreprocessedJsonFiles(Lang.FR, jsonDirPath)
				.setTreeTaggerHome(FunctionalTests.getTaggerPath())
				.execute();
		assertTermIndex(termIndex);
	}


	@Test
	public void fromPreprocessedXmiFiles() {
		String jsonDirPath = Paths.get(FunctionalTests.CORPUS2_PATH.toString(), "xmi").toString();
		TermIndex termIndex = TerminoExtractor
				.fromPreprocessedXmiFiles(Lang.FR, jsonDirPath)
				.setTreeTaggerHome(FunctionalTests.getTaggerPath())
				.execute();
		assertTermIndex(termIndex);
	}


	@Test
	public void fromTxtCorpus() {
		TermIndex termIndex = TerminoExtractor
				.fromTxtCorpus(Lang.FR, FunctionalTests.CORPUS1_PATH.toString(), "**/*.txt", "UTF-8")
				.setTreeTaggerHome(FunctionalTests.getTaggerPath())
				.execute();
		
		assertTermIndex(termIndex);
	}


	private void assertTermIndex(TermIndex termIndex) {
		assertThat(termIndex)
			.hasSize(7)
			.containsTerm("n: énergie", 4);
	}

	@Test
	public void fromCustomDocumentStream() {
		TermIndex termIndex = TerminoExtractor.fromDocumentStream(Lang.FR, documents.stream())
			.setTreeTaggerHome(FunctionalTests.getTaggerPath())
			.execute();
		
		assertThat(termIndex)
			.hasSize(7)
			.containsTerm("n: énergie", 3);
	}

}
