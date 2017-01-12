package fr.univnantes.termsuite.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.jcas.JCas;

import fr.univnantes.termsuite.framework.service.PreprocessorService;
import fr.univnantes.termsuite.model.Terminology;
import fr.univnantes.termsuite.uima.PipelineListener;
import fr.univnantes.termsuite.uima.PreparationPipelineOptions;
import fr.univnantes.termsuite.utils.TermHistory;

public class Preprocessor {
	
	@Inject
	PreprocessorService preprocessorService;
	
	private Path taggerPath;
	private PreparationPipelineOptions options = new PreparationPipelineOptions();
	private Optional<ResourceOptions> resourceOptions = Optional.empty();
	private Optional<TermHistory> history = Optional.empty();
	private Optional<PipelineListener> listener = Optional.empty();
	private List<AnalysisEngineDescription> customAEs = new ArrayList<>();
	
	public Preprocessor setTaggerPath(Path taggerPath) {
		this.taggerPath = taggerPath;
		return this;
	}
	
	public Preprocessor setListener(PipelineListener listener) {
		this.listener = Optional.of(listener);
		return this;
	}
	
	public Preprocessor setHistory(TermHistory history) {
		this.history = Optional.of(history);
		return this;
	}
	
	public Preprocessor setOptions(PreparationPipelineOptions options) {
		this.options = options;
		return this;
	}
	
	public Preprocessor setResourceOptions(ResourceOptions resourceOptions) {
		this.resourceOptions = Optional.of(resourceOptions);
		return this;
	}
	
	public Preprocessor addCustomAE(AnalysisEngineDescription customAE) {
		this.customAEs.add(customAE);
		return this;
	}
	
	public Terminology toPersistentTerminology(TextCorpus textCorpus, String storeUrl) {
		String name = preprocessorService.generateTerminologyName(textCorpus);
		Terminology termino = TermSuite.createPersitentTerminology(storeUrl, textCorpus.getLang(), name);
		preprocessorService.consumeToTerminology(asStream(textCorpus), termino, -1);
		return termino;
	}

	public Terminology toTerminology(TextCorpus textCorpus, boolean withOccurrences) {
		String name = preprocessorService.generateTerminologyName(textCorpus);
		Terminology termino = TermSuite.createTerminology(textCorpus.getLang(), name, withOccurrences);
		preprocessorService.consumeToTerminology(asStream(textCorpus), termino, -1);
		return termino;
	}
	
	public PreparedCorpus toPreparedCorpusJSON(TextCorpus textCorpus, Path jsonDir) {
		final PreparedCorpus targetCorpus = new PreparedCorpus(textCorpus.getLang(), jsonDir, PreparedCorpus.JSON_PATTERN, PreparedCorpus.JSON_EXTENSION);
		
		asStream(textCorpus)
			.forEach(cas -> preprocessorService.consumeToTargetXMICorpus(cas, textCorpus, targetCorpus));
		return targetCorpus;
	}

	public PreparedCorpus toPreparedCorpusXMI(TextCorpus textCorpus, Path xmiDir) {
		final PreparedCorpus targetCorpus = new PreparedCorpus(textCorpus.getLang(), xmiDir, PreparedCorpus.XMI_PATTERN, PreparedCorpus.XMI_EXTENSION);
		
		asStream(textCorpus)
			.forEach(cas -> preprocessorService.consumeToTargetXMICorpus(cas, textCorpus, targetCorpus));
		return targetCorpus;
	}

	public Stream<JCas> asStream(TextCorpus textCorpus) {
		return preprocessorService.prepare(
				textCorpus, 
				taggerPath, 
				options, 
				resourceOptions, 
				history, 
				listener, 
				customAEs.toArray(new AnalysisEngineDescription[customAEs.size()]));
	}
}