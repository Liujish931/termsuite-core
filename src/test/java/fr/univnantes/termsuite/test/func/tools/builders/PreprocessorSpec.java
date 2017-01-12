package fr.univnantes.termsuite.test.func.tools.builders;

import static fr.univnantes.termsuite.test.TermSuiteAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.uima.jcas.JCas;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import fr.univnantes.termsuite.api.TermSuite;
import fr.univnantes.termsuite.api.TextCorpus;
import fr.univnantes.termsuite.model.Lang;
import fr.univnantes.termsuite.test.func.FunctionalTests;
import fr.univnantes.termsuite.types.TermOccAnnotation;
import fr.univnantes.termsuite.types.WordAnnotation;

public class PreprocessorSpec {
	
	@Rule 
	public TemporaryFolder folder = new TemporaryFolder();

	TextCorpus corpus;
	
	@Before
	public void setup() {
		corpus = new TextCorpus(Lang.FR, FunctionalTests.CORPUS1_PATH);
	}
	
	@Test
	public void testJSONOnCorpus1() {
		TermSuite.preprocessor()
			.setTaggerPath(FunctionalTests.getTaggerPath())
			.toPreparedCorpusJSON(corpus, folder.getRoot().toPath());
		
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "file1.json").toFile()).exists();
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "file2.json").toFile()).exists();
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "dir1", "file3.json").toFile()).exists();
	}
	
	@Test
	public void testXMIOnCorpus1() {
		TermSuite.preprocessor()
			.setTaggerPath(FunctionalTests.getTaggerPath())
			.toPreparedCorpusXMI(corpus, folder.getRoot().toPath());
		
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "file1.xmi").toFile()).exists();
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "file2.xmi").toFile()).exists();
		assertThat(Paths.get(folder.getRoot().getAbsolutePath(), "dir1", "file3.xmi").toFile()).exists();
	}

	@Test
	public void testStreamOnCorpus1() {
		Stream<JCas> stream = TermSuite.preprocessor()
			.setTaggerPath(FunctionalTests.getTaggerPath())
			.asStream(corpus);
		
		assertAllDocuments(stream);
	}
	
	private void assertAllDocuments(Stream<JCas> stream) {
		Iterator<JCas> it = stream.iterator();
		JCas cas1 = it.next();
		
		assertThat(cas1)
			.urlEndsWith("file2.txt")
			.containsText("Une éolienne donne de l'énergie.")
			.containsAnnotation(TermOccAnnotation.class, 4, 12)
			.containsAnnotation(TermOccAnnotation.class, 24, 31)
			.containsAnnotation(WordAnnotation.class, 4, 12)
			.containsAnnotation(WordAnnotation.class, 24, 31)
			;
		
		JCas cas2 = it.next();
		
		assertThat(cas2)
			.urlEndsWith("file1.txt")
			.containsText("L'énergie éolienne est l'énergie de demain.")
			.containsAnnotation(TermOccAnnotation.class, 2, 18)
			.containsAnnotation(WordAnnotation.class, 2, 9)
			.containsAnnotation(WordAnnotation.class, 10, 18)
			;

		JCas cas3 = it.next();
		assertThat(cas3)
			.urlEndsWith("dir1/file3.txt")
			.containsText("L'énergie du futur sera l'énergie éolienne.")
			.containsAnnotation(TermOccAnnotation.class, 2, 18)
			.containsAnnotation(WordAnnotation.class, 2, 9)
			.containsAnnotation(WordAnnotation.class, 13, 18)
			;

		assertFalse(it.hasNext());

		Path path1 = Paths.get(
				System.getProperty("user.dir"), 
				FunctionalTests.CORPUS1_PATH.toString(), 
				"file2.txt");
		assertThat(cas1).hasUrl(path1.toString());
		Path path2 = Paths.get(
				System.getProperty("user.dir"), 
				FunctionalTests.CORPUS1_PATH.toString(), 
				"file1.txt");
		assertThat(cas2).hasUrl(path2.toString());
		Path path3 = Paths.get(
				System.getProperty("user.dir"), 
				FunctionalTests.CORPUS1_PATH.toString(), 
				"dir1","file3.txt");
		assertThat(cas3).hasUrl(path3.toString());
	}
}