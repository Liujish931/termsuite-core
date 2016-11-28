/*******************************************************************************
 * Copyright 2015-2016 - CNRS (Centre National de Recherche Scientifique)
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 *******************************************************************************/
package eu.project.ttc.tools;

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import eu.project.ttc.engines.CasStatCounter;
import eu.project.ttc.engines.Contextualizer;
import eu.project.ttc.engines.ContextualizerAE;
import eu.project.ttc.engines.DocumentFrequencySetterAE;
import eu.project.ttc.engines.DocumentLogger;
import eu.project.ttc.engines.EvalEngine;
import eu.project.ttc.engines.ExtensionDetecterAE;
import eu.project.ttc.engines.FixedExpressionSpotter;
import eu.project.ttc.engines.FixedExpressionTermMarker;
import eu.project.ttc.engines.GraphicalVariantGatherer;
import eu.project.ttc.engines.MateLemmaFixer;
import eu.project.ttc.engines.MateLemmatizerTagger;
import eu.project.ttc.engines.Merger;
import eu.project.ttc.engines.PilotSetterAE;
import eu.project.ttc.engines.PipelineObserver;
import eu.project.ttc.engines.PostProcessorAE;
import eu.project.ttc.engines.Ranker;
import eu.project.ttc.engines.RegexSpotter;
import eu.project.ttc.engines.SWTSizeSetterAE;
import eu.project.ttc.engines.SemanticAlignerAE;
import eu.project.ttc.engines.StringRegexFilter;
import eu.project.ttc.engines.TermGathererAE;
import eu.project.ttc.engines.TermIndexBlacklistWordFilterAE;
import eu.project.ttc.engines.TermOccAnnotationImporter;
import eu.project.ttc.engines.TermSpecificityComputer;
import eu.project.ttc.engines.TreeTaggerLemmaFixer;
import eu.project.ttc.engines.cleaner.AbstractTermIndexCleaner;
import eu.project.ttc.engines.cleaner.MaxSizeThresholdCleaner;
import eu.project.ttc.engines.cleaner.TermIndexThresholdCleaner;
import eu.project.ttc.engines.cleaner.TermIndexTopNCleaner;
import eu.project.ttc.engines.desc.Lang;
import eu.project.ttc.engines.desc.TermSuiteCollection;
import eu.project.ttc.engines.desc.TermSuitePipelineException;
import eu.project.ttc.engines.exporter.CompoundExporterAE;
import eu.project.ttc.engines.exporter.EvalExporterAE;
import eu.project.ttc.engines.exporter.ExportVariationRuleExamplesAE;
import eu.project.ttc.engines.exporter.JsonCasExporter;
import eu.project.ttc.engines.exporter.JsonExporterAE;
import eu.project.ttc.engines.exporter.SpotterTSVWriter;
import eu.project.ttc.engines.exporter.TSVExporterAE;
import eu.project.ttc.engines.exporter.TbxExporterAE;
import eu.project.ttc.engines.exporter.TermsuiteJsonCasExporter;
import eu.project.ttc.engines.exporter.VariantEvalExporterAE;
import eu.project.ttc.engines.exporter.VariationExporterAE;
import eu.project.ttc.engines.exporter.XmiCasExporter;
import eu.project.ttc.engines.morpho.CompostAE;
import eu.project.ttc.engines.morpho.ManualCompositionSetter;
import eu.project.ttc.engines.morpho.ManualPrefixSetter;
import eu.project.ttc.engines.morpho.PrefixSplitter;
import eu.project.ttc.engines.morpho.SuffixDerivationDetecter;
import eu.project.ttc.engines.morpho.SuffixDerivationExceptionSetter;
import eu.project.ttc.history.TermHistory;
import eu.project.ttc.history.TermHistoryResource;
import eu.project.ttc.metrics.LogLikelihood;
import eu.project.ttc.models.OccurrenceStore;
import eu.project.ttc.models.OccurrenceType;
import eu.project.ttc.models.RelationType;
import eu.project.ttc.models.Term;
import eu.project.ttc.models.TermIndex;
import eu.project.ttc.models.TermProperty;
import eu.project.ttc.models.index.MemoryTermIndex;
import eu.project.ttc.models.occstore.MemoryOccurrenceStore;
import eu.project.ttc.models.occstore.MongoDBOccurrenceStore;
import eu.project.ttc.readers.AbstractToTxtSaxHandler;
import eu.project.ttc.readers.CollectionDocument;
import eu.project.ttc.readers.EmptyCollectionReader;
import eu.project.ttc.readers.GenericXMLToTxtCollectionReader;
import eu.project.ttc.readers.IstexCollectionReader;
import eu.project.ttc.readers.JsonCollectionReader;
import eu.project.ttc.readers.QueueRegistry;
import eu.project.ttc.readers.StreamingCollectionReader;
import eu.project.ttc.readers.StringCollectionReader;
import eu.project.ttc.readers.TeiCollectionReader;
import eu.project.ttc.readers.TxtCollectionReader;
import eu.project.ttc.readers.XmiCollectionReader;
import eu.project.ttc.resources.CharacterFootprintTermFilter;
import eu.project.ttc.resources.CompostInflectionRules;
import eu.project.ttc.resources.EvalTrace;
import eu.project.ttc.resources.FixedExpressionResource;
import eu.project.ttc.resources.GeneralLanguageResource;
import eu.project.ttc.resources.ManualSegmentationResource;
import eu.project.ttc.resources.MateLemmatizerModel;
import eu.project.ttc.resources.MateTaggerModel;
import eu.project.ttc.resources.ObserverResource;
import eu.project.ttc.resources.PrefixTree;
import eu.project.ttc.resources.ReferenceTermList;
import eu.project.ttc.resources.SimpleWordSet;
import eu.project.ttc.resources.SuffixDerivationList;
import eu.project.ttc.resources.TermIndexResource;
import eu.project.ttc.resources.TermSuiteMemoryUIMAResource;
import eu.project.ttc.resources.TermSuitePipelineObserver;
import eu.project.ttc.resources.YamlVariantRules;
import eu.project.ttc.stream.CasConsumer;
import eu.project.ttc.stream.ConsumerRegistry;
import eu.project.ttc.stream.DocumentProvider;
import eu.project.ttc.stream.DocumentStream;
import eu.project.ttc.stream.StreamingCasConsumer;
import eu.project.ttc.termino.engines.ScorerConfig;
import eu.project.ttc.types.FixedExpression;
import eu.project.ttc.types.TermOccAnnotation;
import eu.project.ttc.types.WordAnnotation;
import eu.project.ttc.utils.FileUtils;
import eu.project.ttc.utils.OccurrenceBuffer;
import fr.free.rocheteau.jerome.engines.Stemmer;
import fr.univnantes.julestar.uima.resources.MultimapFlatResource;
import fr.univnantes.lina.uima.ChineseSegmenterResourceHelper;
import fr.univnantes.lina.uima.engines.ChineseSegmenter;
import fr.univnantes.lina.uima.engines.TreeTaggerWrapper;
import fr.univnantes.lina.uima.models.ChineseSegmentResource;
import fr.univnantes.lina.uima.models.TreeTaggerParameter;
import fr.univnantes.lina.uima.tkregex.ae.RegexListResource;
import fr.univnantes.lina.uima.tkregex.ae.TokenRegexAE;
import uima.sandbox.filter.resources.DefaultFilterResource;
import uima.sandbox.filter.resources.FilterResource;
import uima.sandbox.lexer.engines.Lexer;
import uima.sandbox.lexer.resources.SegmentBank;
import uima.sandbox.lexer.resources.SegmentBankResource;
import uima.sandbox.mapper.engines.Mapper;
import uima.sandbox.mapper.resources.Mapping;
import uima.sandbox.mapper.resources.MappingResource;

/*
 * TODO Integrates frozen expressions
 * TODO integrate Sonar runner
 * TODO Add functional pipeline TestCases for each collection type and for different pipeline configs
 */


/**
 * A collection reader and ae aggregator (builder pattern) that 
 * creates and runs a full pipeline.
 *  
 * @author Damien Cram
 *
 */
public class TermSuitePipeline {

	/* The Logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(TermSuitePipeline.class);
	
	/* ******************************
	 * MAIN PIPELINE PARAMETERS
	 */
	private OccurrenceStore occurrenceStore = new MemoryOccurrenceStore();
	private Optional<? extends TermIndex> termIndex = Optional.empty();
	private Lang lang;
	private CollectionReaderDescription crDescription;
	private String pipelineObserverName;
	private AggregateBuilder aggregateBuilder;
	private String termHistoryResourceName = "PipelineHistory";

	
	/*
	 * POS Tagger parameters
	 */
	private Optional<String> mateModelsPath = Optional.empty();
	private Optional<String> treeTaggerPath = Optional.empty();
	

	/*
	 * Regex Spotter params
	 */
	private boolean addSpottedAnnoToTermIndex = true;
	private boolean spotWithOccurrences = true;
	private Optional<Boolean> logOverlappingRules = Optional.empty();
	private Optional<String> postProcessingStrategy = Optional.empty();
	private boolean enableSyntacticLabels = false;

	/*
	 * Contextualizer options
	 */
	private OccurrenceType contextualizeCoTermsType = OccurrenceType.SINGLE_WORD;
	private int contextualizeWithCoOccurrenceFrequencyThreshhold = 1;
	private String contextAssocRateMeasure = LogLikelihood.class.getName();

	/*
	 * Cleaner properties
	 */
	private boolean keepVariantsWhileCleaning = false;
	
	/*
	 * Compost Params
	 */
	private Optional<Float> alpha = Optional.empty();
	private Optional<Float> beta = Optional.empty();
	private Optional<Float> gamma = Optional.empty();
	private Optional<Float> delta = Optional.empty();
	private Optional<Float> compostScoreThreshold = Optional.empty();
	private Optional<Integer> compostMinComponentSize = Optional.empty();
	private Optional<Integer> compostMaxComponentNum = Optional.empty();
	private Optional<Float> compostSegmentSimilarityThreshold = Optional.of(1f);

	/*
	 * Graphical Variant Gatherer parameters
	 */
	private Optional<Float> graphicalVariantSimilarityThreshold = Optional.empty();
	
	/* JSON */
	private boolean exportJsonWithOccurrences = true;
	private boolean exportJsonWithContext = false;
	private boolean linkMongoStore = false;
	/* TSV */
	private String tsvExportProperties = "groupingKey,wr";
	private boolean tsvWithVariantScores = false;
	private boolean tsvWithHeaders = true;
	
	/*
	 * Streaming parameters
	 */
	private Thread streamThread = null;
	private DocumentProvider documentProvider;


	/* *******************
	 * CONSTRUCTORS
	 */
	private TermSuitePipeline(String lang, String urlPrefix) {
		this.lang = Lang.forName(lang);
		this.aggregateBuilder = new AggregateBuilder();
		this.pipelineObserverName = PipelineObserver.class.getSimpleName() + "-" + Thread.currentThread().getId() + "-" + System.currentTimeMillis();

		TermSuiteResourceManager.getInstance().register(pipelineObserverName, new TermSuitePipelineObserver(2,1));
		
		this.termHistoryResourceName = TermHistory.class.getSimpleName() + "-" + Thread.currentThread().getId() + "-" + System.currentTimeMillis();
		TermSuiteResourceManager.getInstance().register(termHistoryResourceName, new TermHistory());
		
		initUIMALogging();
	}

	
	private void initUIMALogging() {
		System.setProperty("org.apache.uima.logger.class", UIMASlf4jWrapperLogger.class.getName());
	}


	/**
	 * 
	 * Starts a chaining {@link TermSuitePipeline} builder. 
	 * 
	 * @param lang
	 * 			The 
	 * @return
	 * 			The chaining builder.
	 * 
	 */
	public static TermSuitePipeline create(String lang) {
		return new TermSuitePipeline(lang, null);
	}
	

	public static TermSuitePipeline create(TermIndex termIndex) {
		Preconditions.checkNotNull(termIndex.getName(), "The term index must have a name before it can be used in TermSuitePipeline");
		
		if(!TermSuiteResourceManager.getInstance().contains(termIndex.getName()))
			TermSuiteResourceManager.getInstance().register(termIndex.getName(), termIndex);
		
		TermSuitePipeline pipeline = create(termIndex.getLang().getCode());
		pipeline.emptyCollection();
		pipeline.setTermIndex(termIndex);
		
		return pipeline;
	}
	
	/* *******************************
	 * RUNNERS
	 */
	
	/**
	 * Runs the pipeline with {@link SimplePipeline} on the {@link CollectionReader} that must have been defined.
	 * 
	 * @throws TermSuitePipelineException if no {@link CollectionReader} has been declared on this pipeline
	 */
	public TermSuitePipeline run() {
		checkCR();
		runPipeline();
		return this;
	}
	
	private void runPipeline() {
		try {
			SimplePipeline.runPipeline(this.crDescription, createDescription());
			terminates();
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public DocumentStream stream(CasConsumer consumer) {
		try {
			String id = new BigInteger(130, new SecureRandom()).toString(8);
			String casConsumerName = "pipeline-"+id+"-consumer";
			ConsumerRegistry.getInstance().registerConsumer(casConsumerName, consumer);
			String queueName = "pipeline-"+id+"-queue";
			final BlockingQueue<CollectionDocument> q = QueueRegistry.getInstance().registerQueue(queueName, 10);
			
			/*
			 * 1- Creates the streaming collection reader desc
			 */
			this.crDescription = CollectionReaderFactory.createReaderDescription(
					StreamingCollectionReader.class,
					StreamingCollectionReader.PARAM_LANGUAGE, this.lang.getCode(),
					StreamingCollectionReader.PARAM_NAME, queueName,
					StreamingCollectionReader.PARAM_QUEUE_NAME, queueName
					);
			
			/*
			 * 2- Aggregate the consumer AE
			 */
			AnalysisEngineDescription consumerAE = AnalysisEngineFactory.createEngineDescription(
					StreamingCasConsumer.class, 
					StreamingCasConsumer.PARAM_CONSUMER_NAME, casConsumerName
				);
			this.aggregateBuilder.add(consumerAE);
			
			/*
			 * 3- Starts the pipeline in a separate Thread 
			 */
			this.streamThread = new Thread() {
				@Override
				public void run() {
					runPipeline();
				}
			};
			this.streamThread.start();
			
			/*
			 * 4- Bind user inputs to the queue
			 */
			documentProvider = new DocumentProvider() {
				@Override
				public void provide(CollectionDocument doc) {
					try {
						q.put(doc);
					} catch (InterruptedException e) {
						LOGGER.warn("Interrupted while there were more documents waiting.");
					}
				}
			};
			return new DocumentStream(streamThread, documentProvider, consumer, queueName);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public Thread getStreamThread() {
		return streamThread;
	}
	
	private void checkCR() {
		if(crDescription == null)
			throw new TermSuitePipelineException("No collection reader has been declared on this pipeline.");
	}

		
	private void terminates() {
		if(termIndex.isPresent() && termIndex.get().getOccurrenceStore() instanceof MongoDBOccurrenceStore) 
			((MongoDBOccurrenceStore)termIndex.get().getOccurrenceStore()).close();
			
	}

	/**
	 * Registers a pipeline listener.
	 * 
	 * @param pipelineListener
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline addPipelineListener(PipelineListener pipelineListener) {
		TermSuiteResourceManager manager = TermSuiteResourceManager.getInstance();
		((TermSuitePipelineObserver)manager.get(pipelineObserverName)).registerListener(pipelineListener);
		return this;
	}

	
	/**
	 * Runs the pipeline with {@link SimplePipeline} without requiring a {@link CollectionReader}
	 * to be defined.
	 * @param cas the {@link JCas} on which the pipeline operates.
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline run(JCas cas) {
		try {
			SimplePipeline.runPipeline(cas, createDescription());
			terminates();
			return this;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public TermSuitePipeline setInlineString(String text)  {
		try {
			this.crDescription = CollectionReaderFactory.createReaderDescription(
					StringCollectionReader.class,
					StringCollectionReader.PARAM_TEXT, text,
					StringCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
				);
			return this;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	
	public TermSuitePipeline setIstexCollection(String apiURL, List<String> documentsIds) {
		try {
			this.crDescription = CollectionReaderFactory.createReaderDescription(
				IstexCollectionReader.class,
				IstexCollectionReader.PARAM_IGNORE_LANGUAGE_ERRORS, true,
				IstexCollectionReader.PARAM_LANGUAGE, this.lang.getCode(),
				IstexCollectionReader.PARAM_ID_LIST, Joiner.on(",").join(documentsIds),
				IstexCollectionReader.PARAM_API_URL, apiURL
			);
			return this;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	

	/**
	 * Creates a collection reader for this pipeline.
	 * 
	 * @param termSuiteCollection
	 * @param collectionPath
	 * @param collectionEncoding
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setCollection(TermSuiteCollection termSuiteCollection, String collectionPath, String collectionEncoding) {
		Preconditions.checkNotNull(termSuiteCollection);
		Preconditions.checkNotNull(collectionPath);
		Preconditions.checkNotNull(collectionEncoding);
		try {
			switch(termSuiteCollection) {
			case TEI:
				this.crDescription = CollectionReaderFactory.createReaderDescription(
						TeiCollectionReader.class,
						TeiCollectionReader.PARAM_INPUTDIR, collectionPath,
						TxtCollectionReader.PARAM_COLLECTION_TYPE, termSuiteCollection,
						TeiCollectionReader.PARAM_ENCODING, collectionEncoding,
						TeiCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
						);
				break;
			case TXT:
				this.crDescription = CollectionReaderFactory.createReaderDescription(
						TxtCollectionReader.class,
						TxtCollectionReader.PARAM_INPUTDIR, collectionPath,
						TxtCollectionReader.PARAM_COLLECTION_TYPE, termSuiteCollection,
						TxtCollectionReader.PARAM_ENCODING, collectionEncoding,
						TxtCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
						);
				break;
			case XMI:
				this.crDescription = CollectionReaderFactory.createReaderDescription(
						XmiCollectionReader.class,
						XmiCollectionReader.PARAM_INPUTDIR, collectionPath,
						XmiCollectionReader.PARAM_COLLECTION_TYPE, termSuiteCollection,
						XmiCollectionReader.PARAM_ENCODING, collectionEncoding,
						XmiCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
						);
				break;
			case JSON:
				this.crDescription = CollectionReaderFactory.createReaderDescription(
						JsonCollectionReader.class,
						JsonCollectionReader.PARAM_INPUTDIR, collectionPath,
						JsonCollectionReader.PARAM_COLLECTION_TYPE, termSuiteCollection,
						JsonCollectionReader.PARAM_ENCODING, collectionEncoding,
						JsonCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
				);
				break;
			case EMPTY:
				this.crDescription = CollectionReaderFactory.createReaderDescription(
						EmptyCollectionReader.class
						);
				break;
			default:
				throw new IllegalArgumentException("No such collection: " + termSuiteCollection);
			}
			return this;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * Creates a collection reader of type {@link GenericXMLToTxtCollectionReader} for this pipeline.
	 * 
	 * Requires a list of dropped tags and txt tags for collection parsing. 
	 * 
	 * @see AbstractToTxtSaxHandler
	 * 
	 * @param termSuiteCollection
	 * @param collectionPath
	 * @param collectionEncoding
	 * @param droppedTags
	 * @param txtTags
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setCollection(TermSuiteCollection termSuiteCollection, String collectionPath, String collectionEncoding, String droppedTags, String txtTags)  {
		try {
			this.crDescription = CollectionReaderFactory.createReaderDescription(
					GenericXMLToTxtCollectionReader.class,
					GenericXMLToTxtCollectionReader.PARAM_COLLECTION_TYPE, termSuiteCollection,
					GenericXMLToTxtCollectionReader.PARAM_DROPPED_TAGS, droppedTags,
					GenericXMLToTxtCollectionReader.PARAM_TXT_TAGS, txtTags,
					GenericXMLToTxtCollectionReader.PARAM_INPUTDIR, collectionPath,
					GenericXMLToTxtCollectionReader.PARAM_ENCODING, collectionEncoding,
					GenericXMLToTxtCollectionReader.PARAM_LANGUAGE, this.lang.getCode()
					);
			return this;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * Invoke this method if TermSuite resources are accessible via 
	 * a "file:/path/to/res/" url, i.e. they can be found locally.
	 * 
	 * @param resourceDir
	 * @return
	 */
	public TermSuitePipeline setResourceDir(String resourceDir) {
		Preconditions.checkArgument(new File(resourceDir).isDirectory(), 
				"Not a directory: %s", resourceDir);
		
		if(!resourceDir.endsWith(File.separator))
			resourceDir = resourceDir + File.separator;
//		TermSuiteUtils.addToClasspath(resourceDir);
		try {
			this.resourceUrlPrefix = Optional.of(new URL("file:" + resourceDir));
			LOGGER.info("Resource URL prefix is: {}", this.resourceUrlPrefix.get());
		} catch (MalformedURLException e) {
			throw new TermSuitePipelineException(e);
		}
		return this;
	}
	
	public TermSuitePipeline setResourceJar(String resourceJar) {
		Preconditions.checkArgument(FileUtils.isJar(resourceJar), 
				"Not a jar file: %s", resourceJar);
		try {
			this.resourceUrlPrefix = Optional.of(new URL("jar:file:"+resourceJar+"!/"));
			LOGGER.info("Resource URL prefix is: {}", this.resourceUrlPrefix.get());
		} catch (MalformedURLException e) {
			throw new TermSuitePipelineException(e);
		}
		return this;		
	}

	
	
	private Optional<URL> resourceUrlPrefix = Optional.empty();
	
	
	public TermSuitePipeline setResourceUrlPrefix(String urlPrefix) {
		try {
			this.resourceUrlPrefix = Optional.of(new URL(urlPrefix));
		} catch (MalformedURLException e) {
			throw new TermSuitePipelineException("Bad url: " + urlPrefix, e);
		}
		return this;
	}


	public TermSuitePipeline setContextAssocRateMeasure(String contextAssocRateMeasure) {
		this.contextAssocRateMeasure = contextAssocRateMeasure;
		return this;
	}
	
	public TermSuitePipeline emptyCollection() {
		return setCollection(TermSuiteCollection.EMPTY, "", "UTF-8");
	}

	
	public AnalysisEngineDescription createDescription()  {
		try {
			return this.aggregateBuilder.createAggregateDescription();
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public TermSuitePipeline setHistory(TermHistory history) {
		TermSuiteResourceManager.getInstance().remove(termHistoryResourceName);
		TermSuiteResourceManager.getInstance().register(termHistoryResourceName, history);
		return this;
	}

	public TermSuitePipeline watch(String... termKeys) {
		TermHistory termHistory = (TermHistory)TermSuiteResourceManager.getInstance().get(termHistoryResourceName);
		termHistory.addWatchedTerms(termKeys);
		return this;
	}

	public String getHistoryResourceName() {
		return termHistoryResourceName;
	}
		
	public TermSuitePipeline aeWordTokenizer() {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					Lexer.class, 
					Lexer.PARAM_TYPE, "eu.project.ttc.types.WordAnnotation"
				);
			
			ExternalResourceDescription	segmentBank = ExternalResourceFactory.createExternalResourceDescription(
					SegmentBankResource.class,
					getResUrl(TermSuiteResource.SEGMENT_BANK)
				);
			

					
			ExternalResourceFactory.bindResource(
					ae, 
					SegmentBank.KEY_SEGMENT_BANK, 
					segmentBank);

			return aggregateAndReturn(ae, "Word tokenizer", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
		
	}

//	private TermSuitePipeline aggregateAndReturn(AnalysisEngineDescription ae) {
//		return aggregateAndReturn(ae, null, 0);
//	}

	private Map<String, MutableInt> taskNumbers = Maps.newHashMap();
	private String getNumberedTaskName(String taskName) {
		if(!taskNumbers.containsKey(taskName))
			taskNumbers.put(taskName, new MutableInt(0));
		taskNumbers.get(taskName).increment();
		return String.format("%s-%d", taskName, taskNumbers.get(taskName).intValue());
	}
	
	private TermSuitePipeline aggregateAndReturn(AnalysisEngineDescription ae, String taskName, int ccWeight) {
		Preconditions.checkNotNull(taskName);

		// Add the pre-task observer
		this.aggregateBuilder.add(aeObserver(taskName, ccWeight, PipelineObserver.TASK_STARTED));
		
		// Add the ae itself
		this.aggregateBuilder.add(ae);
		
		// Add the post-task observer
		this.aggregateBuilder.add(aeObserver(taskName, ccWeight, PipelineObserver.TASK_ENDED));
		return this;
	}


	private AnalysisEngineDescription aeObserver(String taskName, int weight, String hook) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					PipelineObserver.class, 
					PipelineObserver.TASK_NAME, taskName,
					PipelineObserver.HOOK, hook,
					PipelineObserver.WEIGHT, weight
				);
			
			ExternalResourceFactory.bindResource(ae, resObserver());

			return ae;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
		
	}
	public TermSuitePipeline aeTreeTagger() {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TreeTaggerWrapper.class, 
					TreeTaggerWrapper.PARAM_ANNOTATION_TYPE, "eu.project.ttc.types.WordAnnotation",
					TreeTaggerWrapper.PARAM_TAG_FEATURE, "tag",
					TreeTaggerWrapper.PARAM_LEMMA_FEATURE, "lemma",
					TreeTaggerWrapper.PARAM_UPDATE_ANNOTATION_FEATURES, true,
					TreeTaggerWrapper.PARAM_TT_HOME_DIRECTORY, this.treeTaggerPath.get()
				);
			
			ExternalResourceDescription ttParam = ExternalResourceFactory.createExternalResourceDescription(
					TreeTaggerParameter.class,
					getResUrl(TermSuiteResource.TREETAGGER_CONFIG, Tagger.TREE_TAGGER)
				);
			
			ExternalResourceFactory.bindResource(
					ae,
					TreeTaggerParameter.KEY_TT_PARAMETER, 
					ttParam 
				);

			return aggregateAndReturn(ae, "POS Tagging (TreeTagger)", 0).ttLemmaFixer().ttNormalizer();
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}


	/*
	 * Builds the resource url for this pipeline
	 */
	private URL getResUrl(TermSuiteResource tsResource, Tagger tagger) {
		if(!resourceUrlPrefix.isPresent())
			return tsResource.fromClasspath(lang, tagger);
		else
			return tsResource.fromUrlPrefix(this.resourceUrlPrefix.get(), lang, tagger);		
		
	}


	/*
	 * Builds the resource url for this pipeline	 * 
	 */
	private URL getResUrl(TermSuiteResource tsResource) {
		if(!resourceUrlPrefix.isPresent()) {
			URL fromClasspath = tsResource.fromClasspath(lang);
			return fromClasspath;
		} else {
			URL fromUrlPrefix = tsResource.fromUrlPrefix(this.resourceUrlPrefix.get(), lang);
			return fromUrlPrefix;
		}		
	}

	public TermSuitePipeline setMateModelPath(String path) {
		this.mateModelsPath = Optional.of(path);
		Preconditions.checkArgument(Files.exists(Paths.get(path)), "Directory %s does not exist", path);
		Preconditions.checkArgument(Files.isDirectory(Paths.get(path)), "File %s is not a directory", path);
		return this;
	}
	
	public TermSuitePipeline aeMateTaggerLemmatizer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					MateLemmatizerTagger.class
				);
			
			Preconditions.checkState(mateModelsPath.isPresent(), "The path to mate models must be explicitely given. See method #setMateModelPath");
			String lemmatizerModel = Paths.get(mateModelsPath.get(), "mate-lemma-"+lang.getCode()+".model").toString();
			String taggerModel = Paths.get(mateModelsPath.get(), "mate-pos-"+lang.getCode()+".model").toString();
			Preconditions.checkArgument(Files.exists(Paths.get(lemmatizerModel)), "Lemmatizer model does not exist: %s", lemmatizerModel);
			Preconditions.checkArgument(Files.exists(Paths.get(taggerModel)), "Tagger model does not exist: %s", taggerModel);
	
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					MateLemmatizerTagger.LEMMATIZER, 
					MateLemmatizerModel.class, 
					lemmatizerModel);
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					MateLemmatizerTagger.TAGGER, 
					MateTaggerModel.class, 
					taggerModel);
	
			return aggregateAndReturn(ae, "POS Tagging (Mate)", 0)
					.mateLemmaFixer()
					.mateNormalizer();
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * Defines the term properties that appear in tsv export file
	 * 
	 * @see #haeTsvExporter(String)
	 * @param properties
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setTsvExportProperties(TermProperty... properties) {
		this.tsvExportProperties = Joiner.on(",").join(properties);
		return this;
	}
	
	/**
	 * Exports the {@link TermIndex} in tsv format
	 * 
	 * @see #setTsvExportProperties(TermProperty...)
	 * @param toFilePath
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeTsvExporter(String toFilePath) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TSVExporterAE.class, 
					TSVExporterAE.TO_FILE_PATH, toFilePath,
					TSVExporterAE.TERM_PROPERTIES, this.tsvExportProperties,
					TSVExporterAE.SHOW_HEADERS, tsvWithHeaders,
					TSVExporterAE.SHOW_SCORES, tsvWithVariantScores
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());


			return aggregateAndReturn(ae, getNumberedTaskName("Exporting the terminology to " + toFilePath), 1);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * 
	 * Exports examples of matching pairs for each variation rule.
	 * 
	 * @param toFilePath
	 * 				the file path where to write the examples for each variation rules
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeExportVariationRuleExamples(String toFilePath) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					ExportVariationRuleExamplesAE.class, ExportVariationRuleExamplesAE.TO_FILE_PATH, toFilePath);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resSyntacticVariantRules());

			return aggregateAndReturn(ae, "Exporting variation rules examples", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * 
	 * Exports all compound words of the terminology to given file path.
	 * 
	 * @param toFilePath
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeCompoundExporter(String toFilePath) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					CompoundExporterAE.class, 
					CompoundExporterAE.TO_FILE_PATH, 
					toFilePath);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Exporting compounds", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public TermSuitePipeline haeVariationExporter(String toFilePath, RelationType... vTypes) {
		try {
			String typeStrings = Joiner.on(",").join(vTypes);
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					VariationExporterAE.class, 
					VariationExporterAE.TO_FILE_PATH, toFilePath,
					VariationExporterAE.VARIATION_TYPES, typeStrings 
					);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			String taskName = "Exporting variations " + typeStrings + " to file " + toFilePath;
			return aggregateAndReturn(ae, taskName, 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
		
	public TermSuitePipeline haeTbxExporter(String toFilePath) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TbxExporterAE.class, 
					TbxExporterAE.TO_FILE_PATH, toFilePath
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, getNumberedTaskName("Exporting the terminology to " + toFilePath), 1);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public TermSuitePipeline haeEvalExporter(String toFilePath, boolean withVariants) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					EvalExporterAE.class, 
					EvalExporterAE.TO_FILE_PATH, toFilePath,
					EvalExporterAE.WITH_VARIANTS, withVariants
					
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Exporting evaluation files", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public TermSuitePipeline setExportJsonWithOccurrences(boolean exportJsonWithOccurrences) {
		this.exportJsonWithOccurrences = exportJsonWithOccurrences;
		return this;
	}
	
	public TermSuitePipeline setExportJsonWithContext(boolean b) {
		this.exportJsonWithContext = b;
		return this;
	}

	
	public TermSuitePipeline haeJsonExporter(String toFilePath)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					JsonExporterAE.class, 
					JsonExporterAE.TO_FILE_PATH, toFilePath,
					JsonExporterAE.WITH_OCCURRENCE, exportJsonWithOccurrences,
					JsonExporterAE.WITH_CONTEXTS, exportJsonWithContext,
					JsonExporterAE.LINKED_MONGO_STORE, this.linkMongoStore
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, getNumberedTaskName("Exporting the terminology to " + toFilePath), 1);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}


	/**
	 * 
	 * Creates a tsv output with :
	 *  - the occurrence list of each term and theirs in-text contexts.
	 *  - a json structure for the evaluation of each variant
	 * 
	 * @param toFilePath
	 * 			The output file path
	 * @param topN
	 * 			The number of variants to keep in the file
	 * @param maxVariantsPerTerm
	 * 			The maximum number of variants to eval for each term
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeVariantEvalExporter(String toFilePath, int topN, int maxVariantsPerTerm)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					VariantEvalExporterAE.class, 
					VariantEvalExporterAE.TO_FILE_PATH, toFilePath,
					VariantEvalExporterAE.TOP_N, topN,
					VariantEvalExporterAE.NB_VARIANTS_PER_TERM, maxVariantsPerTerm
				);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Exporting variant evaluation files", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	private void addParameters(AnalysisEngineDescription ae, Object... parameters) {
		if(parameters.length % 2 == 1)
			throw new IllegalArgumentException("Expecting even number of arguements for key-value pairs: " + parameters.length);
		for(int i=0; i<parameters.length; i+=2) 
			ae.getMetaData().getConfigurationParameterSettings().setParameterValue((String)parameters[i], parameters[i+1]);
	}

	private TermSuitePipeline subNormalizer(String target, URL mappingFile)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					Mapper.class, 
					Mapper.PARAM_SOURCE, "eu.project.ttc.types.WordAnnotation:tag",
					Mapper.PARAM_TARGET, target,
					Mapper.PARAM_UPDATE, true
				);
			
			ExternalResourceDescription mappingRes = ExternalResourceFactory.createExternalResourceDescription(
					MappingResource.class,
					mappingFile
				);
			
			ExternalResourceFactory.bindResource(
					ae,
					Mapping.KEY_MAPPING, 
					mappingRes 
				);

			return aggregateAndReturn(ae, "Normalizing " + mappingFile, 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	private TermSuitePipeline caseNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:case", 
				getResUrl(TermSuiteResource.TAGGER_CASE_MAPPING, tagger));
	}

	private TermSuitePipeline categoryNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:category", 
				getResUrl(TermSuiteResource.TAGGER_CATEGORY_MAPPING, tagger));
	}

	private TermSuitePipeline tenseNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:tense", 
				getResUrl(TermSuiteResource.TAGGER_TENSE_MAPPING, tagger));
	}

	private TermSuitePipeline subCategoryNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:subCategory", 
				getResUrl(TermSuiteResource.TAGGER_SUBCATEGORY_MAPPING, tagger));
	}

	
	private TermSuitePipeline moodNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:mood", 
				getResUrl(TermSuiteResource.TAGGER_MOOD_MAPPING, tagger));
	}

	
	private TermSuitePipeline numberNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:number", 
				getResUrl(TermSuiteResource.TAGGER_NUMBER_MAPPING, tagger));
	}

	
	private TermSuitePipeline genderNormalizer(Tagger tagger)  {
		return subNormalizer(
				"eu.project.ttc.types.WordAnnotation:gender", 
				getResUrl(TermSuiteResource.TAGGER_GENDER_MAPPING, tagger));
	}

	private TermSuitePipeline mateNormalizer()  {
		return normalizer(Tagger.MATE);
	}

	private TermSuitePipeline ttNormalizer()  {
		return normalizer(Tagger.TREE_TAGGER);
	}

	private TermSuitePipeline normalizer(Tagger tagger)  {
		categoryNormalizer(tagger);
		subCategoryNormalizer(tagger);
		moodNormalizer(tagger);
		tenseNormalizer(tagger);
		genderNormalizer(tagger);
		numberNormalizer(tagger);
		return caseNormalizer(tagger);
	}
	
	public TermSuitePipeline aeStemmer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					Stemmer.class,
					Stemmer.PARAM_FEATURE, "eu.project.ttc.types.WordAnnotation:stem",
					Stemmer.PARAM_LANGUAGE, lang,
					Stemmer.PARAM_UPDATE, true
				);

			return aggregateAndReturn(ae, "Stemming", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	

	private TermSuitePipeline ttLemmaFixer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TreeTaggerLemmaFixer.class,
					TreeTaggerLemmaFixer.LANGUAGE, lang.getCode()
				);
			

			return aggregateAndReturn(ae, "Fixing lemmas", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	private TermSuitePipeline mateLemmaFixer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					MateLemmaFixer.class,
					MateLemmaFixer.LANGUAGE, lang.getCode()
				);

			return aggregateAndReturn(ae, "Fixing lemmas", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Iterates over the {@link TermIndex} and mark terms as
	 * "fixed expressions" when their lemmas are found in the 
	 * {@link FixedExpressionResource}.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeFixedExpressionTermMarker()  {
		/*
		 * TODO Check if resource is present for that current language.
		 */
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					FixedExpressionTermMarker.class
				);
			
			ExternalResourceDescription fixedExprRes = ExternalResourceFactory.createExternalResourceDescription(
					FixedExpressionResource.class, 
					getResUrl(TermSuiteResource.FIXED_EXPRESSIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					FixedExpressionResource.FIXED_EXPRESSION_RESOURCE, 
					fixedExprRes
				);
			

			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Marking fixed expression terms", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * Spots fixed expressions in the CAS an creates {@link FixedExpression}
	 * annotation whenever one is found.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeFixedExpressionSpotter()  {
		/*
		 * TODO Check if resource is present for that current language.
		 */
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					FixedExpressionSpotter.class,
					FixedExpressionSpotter.FIXED_EXPRESSION_MAX_SIZE, 5,
					FixedExpressionSpotter.REMOVE_WORD_ANNOTATIONS_FROM_CAS, false,
					FixedExpressionSpotter.REMOVE_TERM_OCC_ANNOTATIONS_FROM_CAS, true
				);
			
			

			ExternalResourceDescription fixedExprRes = ExternalResourceFactory.createExternalResourceDescription(
					FixedExpressionResource.class, 
					getResUrl(TermSuiteResource.FIXED_EXPRESSIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					FixedExpressionResource.FIXED_EXPRESSION_RESOURCE, 
					fixedExprRes
				);
			
			return aggregateAndReturn(ae, "Spotting fixed expressions", 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * The single-word and multi-word term spotter AE
	 * base on UIMA Tokens Regex.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeRegexSpotter()  {
		try {
			Serializable postProcStrategy = this.postProcessingStrategy.isPresent() ? this.postProcessingStrategy.get() : lang.getRegexPostProcessingStrategy();
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					RegexSpotter.class,
					TokenRegexAE.PARAM_ALLOW_OVERLAPPING_OCCURRENCES, true,
					RegexSpotter.POST_PROCESSING_STRATEGY, postProcStrategy
				);
			
			if(enableSyntacticLabels)
				addParameters(
						ae, 
						TokenRegexAE.PARAM_SET_LABELS, "labels");
			
			if(logOverlappingRules.isPresent())
				addParameters(
						ae, 
						RegexSpotter.LOG_OVERLAPPING_RULES, logOverlappingRules.get());
			
			
			ExternalResourceDescription mwtRules = ExternalResourceFactory.createExternalResourceDescription(
					RegexListResource.class, 
					getResUrl(TermSuiteResource.MWT_RULES));
			
			ExternalResourceFactory.bindResource(
					ae,
					RegexListResource.KEY_TOKEN_REGEX_RULES, 
					mwtRules
				);

			ExternalResourceFactory.bindResource(
					ae, resHistory());

	
			ExternalResourceDescription allowedCharsRes = ExternalResourceFactory.createExternalResourceDescription(
					CharacterFootprintTermFilter.class, 
					getResUrl(TermSuiteResource.ALLOWED_CHARS));
			
			ExternalResourceFactory.bindResource(
					ae,
					RegexSpotter.CHARACTER_FOOTPRINT_TERM_FILTER, 
					allowedCharsRes
				);

			if(this.addSpottedAnnoToTermIndex)
				ExternalResourceFactory.bindResource(ae, resTermIndex());

			ExternalResourceDescription stopWordsRes = ExternalResourceFactory.createExternalResourceDescription(
					DefaultFilterResource.class, 
					getResUrl(TermSuiteResource.STOP_WORDS_FILTER));
			
			ExternalResourceFactory.bindResource(
					ae,
					RegexSpotter.STOP_WORD_FILTER, 
					stopWordsRes
				);

			return aggregateAndReturn(ae, "Spotting terms", 0).aeTermOccAnnotationImporter();
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	
	/**
	 * An AE thats imports all {@link TermOccAnnotation} in CAS to a {@link TermIndex}.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeTermOccAnnotationImporter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermOccAnnotationImporter.class,
					TermOccAnnotationImporter.KEEP_OCCURRENCES_IN_TERM_INDEX, spotWithOccurrences
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "TermOccAnnotation importer", 0)
						.aePilotSetter()
						.aeDocumentFrequencySetter()
						.aeSWTSizeSetter()
						;
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	private TermSuitePipeline aePilotSetter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					PilotSetterAE.class
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, PilotSetterAE.TASK_NAME, 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}		
	}
	
	private TermSuitePipeline aeDocumentFrequencySetter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					DocumentFrequencySetterAE.class
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, DocumentFrequencySetterAE.TASK_NAME, 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}		
	}

	private TermSuitePipeline aeSWTSizeSetter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					SWTSizeSetterAE.class
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, SWTSizeSetterAE.TASK_NAME, 0);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}		
	}

	
	/**
	 * Naive morphological analysis of prefix compounds based on a 
	 * prefix dictionary resource
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aePrefixSplitter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					PrefixSplitter.class
				);
			
			ExternalResourceDescription prefixTreeRes = ExternalResourceFactory.createExternalResourceDescription(
					PrefixTree.class, 
					getResUrl(TermSuiteResource.PREFIX_BANK));
			
			ExternalResourceFactory.bindResource(
					ae,
					PrefixTree.PREFIX_TREE, 
					prefixTreeRes
				);

			ExternalResourceFactory.bindResource(ae, resHistory());
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			
			return aggregateAndReturn(ae, "Splitting prefixes", 0)
					.aePrefixExceptionsSetter();
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public TermSuitePipeline aeSuffixDerivationDetector()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					SuffixDerivationDetecter.class
				);
			
			ExternalResourceDescription suffixDerivationsRes = ExternalResourceFactory.createExternalResourceDescription(
					SuffixDerivationList.class,
					getResUrl(TermSuiteResource.SUFFIX_DERIVATIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					SuffixDerivationList.SUFFIX_DERIVATIONS, 
					suffixDerivationsRes
				);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Detecting suffix derivations prefixes", 0)
						.aeSuffixDerivationException();
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	private TermSuitePipeline aeSuffixDerivationException()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					SuffixDerivationExceptionSetter.class
				);
			
			ExternalResourceDescription suffixDerivationsExceptionsRes = ExternalResourceFactory.createExternalResourceDescription(
					MultimapFlatResource.class,
					getResUrl(TermSuiteResource.SUFFIX_DERIVATION_EXCEPTIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					SuffixDerivationExceptionSetter.SUFFIX_DERIVATION_EXCEPTION, 
					suffixDerivationsExceptionsRes
				);

			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Setting suffix derivation exceptions", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}

	}

	

	private TermSuitePipeline aeManualCompositionSetter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					ManualCompositionSetter.class
				);
			
			ExternalResourceDescription manualCompositionListRes = ExternalResourceFactory.createExternalResourceDescription(
					ManualSegmentationResource.class,
					getResUrl(TermSuiteResource.MANUAL_COMPOSITIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					ManualCompositionSetter.MANUAL_COMPOSITION_LIST, 
					manualCompositionListRes
				);


			ExternalResourceFactory.bindResource(ae, resTermIndex());
			
			return aggregateAndReturn(ae, "Setting manual composition", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}

	}

	private TermSuitePipeline aePrefixExceptionsSetter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					ManualPrefixSetter.class
				);
			
			
			ExternalResourceDescription prefixExceptionsRes = ExternalResourceFactory.createExternalResourceDescription(
					ManualSegmentationResource.class,
					getResUrl(TermSuiteResource.PREFIX_EXCEPTIONS));
			
			ExternalResourceFactory.bindResource(
					ae,
					ManualPrefixSetter.PREFIX_EXCEPTIONS, 
					prefixExceptionsRes
				);

			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Setting prefix exceptions", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}

	}

	
	/**
	 * Removes from the term index any term having a 
	 * stop word at its boundaries.
	 * 
	 * @see TermIndexBlacklistWordFilterAE
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeStopWordsFilter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermIndexBlacklistWordFilterAE.class
				);
			
			ExternalResourceDescription stopWordsFilterResourceRes = ExternalResourceFactory.createExternalResourceDescription(
					DefaultFilterResource.class, 
					getResUrl(TermSuiteResource.STOP_WORDS_FILTER));
			
			ExternalResourceFactory.bindResource(
					ae,
					FilterResource.KEY_FILTERS, 
					stopWordsFilterResourceRes
				);

			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Filtering stop words", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	


	
	/**
	 * Exports all CAS as XMI files to a given directory.
	 * 
	 * @param toDirectoryPath
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeXmiCasExporter(String toDirectoryPath)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					XmiCasExporter.class,
					XmiCasExporter.OUTPUT_DIRECTORY, toDirectoryPath
				);

			return aggregateAndReturn(ae, "Exporting XMI Cas files", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Exports all CAS as JSON files to a given directory.
	 *
	 * @param toDirectoryPath
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeTermsuiteJsonCasExporter(String toDirectoryPath)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermsuiteJsonCasExporter.class,
					TermsuiteJsonCasExporter.OUTPUT_DIRECTORY, toDirectoryPath
			);

			return aggregateAndReturn(ae, "Exporting Json Cas files", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Export all CAS in TSV format to a given directory.
	 * 
	 * @see SpotterTSVWriter
	 * @param toDirectoryPath
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeSpotterTSVWriter(String toDirectoryPath)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					SpotterTSVWriter.class,
					XmiCasExporter.OUTPUT_DIRECTORY, toDirectoryPath
				);

			return aggregateAndReturn(ae, "Exporting annotations in TSV to " + toDirectoryPath, 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public TermSuitePipeline aeDocumentLogger(long nbDocument)  {
		
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					DocumentLogger.class,
					DocumentLogger.NB_DOCUMENTS, nbDocument
				);

			return aggregateAndReturn(ae, "Document logging", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Tokenizer for chinese collections.
	 * @see ChineseSegmenter
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeChineseTokenizer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					ChineseSegmenter.class,
					ChineseSegmenter.ANNOTATION_TYPE, "eu.project.ttc.types.WordAnnotation"
				);
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					ChineseSegmenter.CHINESE_WORD_SEGMENTS, 
					ChineseSegmentResource.class, 
					ChineseSegmenterResourceHelper.getChineseWordSegments());
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					ChineseSegmenter.CHINESE_FOREIGN_NAME_SEGMENTS, 
					ChineseSegmentResource.class, 
					ChineseSegmenterResourceHelper.getForeignNameSegments());
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					ChineseSegmenter.CHINESE_NUMBER_SEGMENTS, 
					ChineseSegmentResource.class, 
					ChineseSegmenterResourceHelper.getNumberSegments());

			return aggregateAndReturn(ae, "Word tokenizing", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	private ExternalResourceDescription termSynonymDesc;
	public ExternalResourceDescription resSynonyms() {
		if(termSynonymDesc == null) {
			termSynonymDesc = ExternalResourceFactory.createExternalResourceDescription(
					MultimapFlatResource.class, 
					getResUrl(TermSuiteResource.SYNONYMS));
		}
		return termSynonymDesc;
	}

	private ExternalResourceDescription termIndexResourceDesc;
	public ExternalResourceDescription resTermIndex() {
		if(termIndexResourceDesc == null) {
			if(!termIndex.isPresent())
				emptyTermIndex(UUID.randomUUID().toString());
			
			termIndexResourceDesc = ExternalResourceFactory.createExternalResourceDescription(
					TermIndexResource.class, 
					termIndex.get().getName());
			
			TermSuiteResourceManager manager = TermSuiteResourceManager.getInstance();
			
			// register the term index if not in term index manager
			if(!manager.contains(termIndex.get().getName()))
				manager.register(termIndex.get().getName(), termIndex.get());
		}
		return termIndexResourceDesc;
		
	}
	
	private ExternalResourceDescription pipelineObserverResource;
	public ExternalResourceDescription resObserver() {
		if(pipelineObserverResource == null) {
			pipelineObserverResource = ExternalResourceFactory.createExternalResourceDescription(
					ObserverResource.class, this.pipelineObserverName);
		}
		return pipelineObserverResource;

	}
	
	private ExternalResourceDescription termHistoryResource;
	public ExternalResourceDescription resHistory() {
		if(termHistoryResource == null) {
			termHistoryResource = ExternalResourceFactory.createExternalResourceDescription(
					TermHistoryResource.class, this.termHistoryResourceName);
		}
		return termHistoryResource;

	}

	
	private ExternalResourceDescription syntacticVariantRules;
	public ExternalResourceDescription resSyntacticVariantRules() {
		if(syntacticVariantRules == null) {
			syntacticVariantRules = ExternalResourceFactory.createExternalResourceDescription(
					YamlVariantRules.class, 
					getResUrl(TermSuiteResource.VARIANTS)
				);
		}
		return syntacticVariantRules;

	}


	/**
	 * Returns the term index produced (or last modified) by this pipeline.
	 * @return
	 * 		The term index processed by this pipeline
	 */
	public TermIndex getTermIndex() {
		return this.termIndex.get();
	}
	
	/**
	 * Sets the term index on which this pipeline will run.
	 * 
	 * @param termIndex
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setTermIndex(TermIndex termIndex) {
		this.termIndex = Optional.of(termIndex);
		return this;
	}
	
	/**
	 * Creates a new in-memory {@link TermIndex} on which this 
	 * piepline with run.
	 * 
	 * @param name
	 * 			the name of the new term index
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline emptyTermIndex(String name) {
		MemoryTermIndex termIndex = new MemoryTermIndex(name, this.lang, this.occurrenceStore);
		LOGGER.info("Creating TermIndex {}", termIndex.getName());
		this.termIndex = Optional.of(termIndex);
		return this;
	}

	
	
	private ExternalResourceDescription generalLanguageResourceDesc;
	private ExternalResourceDescription resGeneralLanguage() {
		if(generalLanguageResourceDesc == null)
			generalLanguageResourceDesc = ExternalResourceFactory.createExternalResourceDescription(
					GeneralLanguageResource.class, 
					getResUrl(TermSuiteResource.GENERAL_LANGUAGE));
		return generalLanguageResourceDesc;
	}
	
	/**
	 * Computes {@link TermProperty#WR} values (and additional 
	 * term properties of type {@link TermProperty} in the future).
	 * 
	 * @see TermSpecificityComputer
	 * @see TermProperty
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeSpecificityComputer()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermSpecificityComputer.class
				);
			ExternalResourceFactory.bindResource(ae, resGeneralLanguage());
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Computing term specificities", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	
	public TermSuitePipeline aeSemanticAligner()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					SemanticAlignerAE.class
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			if(TermSuiteResource.SYNONYMS.exists(lang))
				ExternalResourceFactory.bindResource(ae, SemanticAlignerAE.SYNONYMS, resSynonyms());
			ExternalResourceFactory.bindResource(ae, resHistory());
			ExternalResourceFactory.bindResource(ae, resSyntacticVariantRules());

			return aggregateAndReturn(ae, "Computing semantic gathering (alignment)", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	
	public TermSuitePipeline setContextualizeCoTermsType(
			OccurrenceType contextualizeCoTermsType) {
		this.contextualizeCoTermsType = contextualizeCoTermsType;
		return this;
	}
	
	public TermSuitePipeline setContextualizeWithCoOccurrenceFrequencyThreshhold(
			int contextualizeWithCoOccurrenceFrequencyThreshhold) {
		this.contextualizeWithCoOccurrenceFrequencyThreshhold = contextualizeWithCoOccurrenceFrequencyThreshhold;
		return this;
	}
	
	/**
	 * Computes the {@link Contextualizer} vector of all 
	 * single-word terms in the term index.
	 * 
	 * @see Contextualizer
	 * @param scope
	 * @param allTerms
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeContextualizer(int scope, boolean allTerms) {
		AnalysisEngineDescription ae;
		try {
			ae = AnalysisEngineFactory.createEngineDescription(
					ContextualizerAE.class,
					ContextualizerAE.SCOPE, scope,
					ContextualizerAE.CO_TERMS_TYPE, contextualizeCoTermsType,
					ContextualizerAE.COMPUTE_CONTEXTS_FOR_ALL_TERMS, allTerms,
					ContextualizerAE.ASSOCIATION_RATE, contextAssocRateMeasure,
					ContextualizerAE.MINIMUM_COOCC_FREQUENCY_THRESHOLD, contextualizeWithCoOccurrenceFrequencyThreshhold
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Build context vectors", 1);
		} catch (Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public TermSuitePipeline aeMaxSizeThresholdCleaner(TermProperty property, int maxSize) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
				MaxSizeThresholdCleaner.class,
				AbstractTermIndexCleaner.CLEANING_PROPERTY, property,	
				MaxSizeThresholdCleaner.MAX_SIZE, maxSize
			);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Cleaning TermIndex on property "+property.toString().toLowerCase()+" with maxSize=" + maxSize, 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
		
	}

	
	public TermSuitePipeline aeThresholdCleaner(TermProperty property, float threshold, boolean isPeriodic, int cleaningPeriod, int termIndexSizeTrigger) {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
				TermIndexThresholdCleaner.class,
				AbstractTermIndexCleaner.CLEANING_PROPERTY, property,
				AbstractTermIndexCleaner.NUM_TERMS_CLEANING_TRIGGER, termIndexSizeTrigger,
				AbstractTermIndexCleaner.KEEP_VARIANTS, this.keepVariantsWhileCleaning,
				TermIndexThresholdCleaner.THRESHOLD, threshold
			);
			setPeriodic(isPeriodic, cleaningPeriod, ae);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, getNumberedTaskName("Cleaning"), 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	private void setPeriodic(boolean isPeriodic, int cleaningPeriod,
			AnalysisEngineDescription ae) {
		if(isPeriodic)
			addParameters(ae, 
					AbstractTermIndexCleaner.PERIODIC_CAS_CLEAN_ON, true,
					AbstractTermIndexCleaner.CLEANING_PERIOD, cleaningPeriod
				);
	}
	
	/**
	 * 
	 * 
	 * 
	 * @param property
	 * @param threshold
	 * @param cleaningPeriod
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeThresholdCleanerPeriodic(TermProperty property, float threshold, int cleaningPeriod)   {
		return aeThresholdCleaner(property, threshold, true, cleaningPeriod, 0);
	}

	public TermSuitePipeline aeThresholdCleanerSizeTrigger(TermProperty property, float threshold, int termIndexSizeTrigger)   {
		return aeThresholdCleaner(property, threshold, false, 0, termIndexSizeTrigger);
	}

	
	public TermSuitePipeline setKeepVariantsWhileCleaning(boolean keepVariantsWhileCleaning) {
		this.keepVariantsWhileCleaning = keepVariantsWhileCleaning;
		return this;
	}
	
	public TermSuitePipeline aeThresholdCleaner(TermProperty property, float threshold) {
		return aeThresholdCleaner(property, threshold, false, 0, 0);
	}

	public TermSuitePipeline aeTopNCleaner(TermProperty property, int n)  {
		return aeTopNCleanerPeriodic(property, n, false, 0);
	}
	
	/**
	 * 
	 * @param property
	 * @param n
	 * @param isPeriodic
	 * @param cleaningPeriod
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeTopNCleanerPeriodic(TermProperty property, int n, boolean isPeriodic, int cleaningPeriod)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermIndexTopNCleaner.class,
					AbstractTermIndexCleaner.CLEANING_PROPERTY, property,
					TermIndexTopNCleaner.TOP_N, n
					);
			setPeriodic(isPeriodic, cleaningPeriod, ae);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Cleaning TermIndex. Keepings only top " + n + " terms on property " + property.toString().toLowerCase(), 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	public TermSuitePipeline setGraphicalVariantSimilarityThreshold(float th) {
		this.graphicalVariantSimilarityThreshold = Optional.of(th);
		return this;
	}
	
	public TermSuitePipeline aeGraphicalVariantGatherer()   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					GraphicalVariantGatherer.class,
					GraphicalVariantGatherer.LANG, lang.getCode(),
					GraphicalVariantGatherer.SIMILARITY_THRESHOLD, graphicalVariantSimilarityThreshold.isPresent() ? graphicalVariantSimilarityThreshold.get() : 0.9f
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resObserver());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, GraphicalVariantGatherer.TASK_NAME, 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Filters out URLs from CAS.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeUrlFilter()   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					StringRegexFilter.class
				);

			return aggregateAndReturn(ae, "Filtering URLs", 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * Gathers terms according to their syntactic structures.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeTermVariantGatherer()   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					TermGathererAE.class
				);
			
			ExternalResourceFactory.bindResource(ae, resSyntacticVariantRules());
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resObserver());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, TermGathererAE.TASK_NAME, 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	
	/**
	 * Detects all inclusion/extension relation between terms that have size >= 2.
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeExtensionDetector()   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					ExtensionDetecterAE.class
				);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resHistory());

			return aggregateAndReturn(ae, "Detecting term extensions", 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * Transforms the {@link TermIndex} into a flat one-n scored model.
	 * 
	 * 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeScorer(ScorerConfig scorerConfig)   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					PostProcessorAE.class					
				);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resObserver());
			ExternalResourceFactory.bindResource(ae, resHistory());
			
			TermSuiteResourceManager.getInstance().register("ScorerConfigResourceURI", scorerConfig);
			ExternalResourceDescription scorerConfigDescription = ExternalResourceFactory.createExternalResourceDescription(
					TermSuiteMemoryUIMAResource.class,
					"ScorerConfigResourceURI"
				);
			ExternalResourceFactory.bindResource(
					ae,
					PostProcessorAE.SCORER_CONFIG, 
					scorerConfigDescription 
				);


			return aggregateAndReturn(ae, PostProcessorAE.TASK_NAME, 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 *  Merges the variants (only those who are extensions of the base term) 
	 *  of a terms by graphical variation.
	 *  
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline aeMerger()   {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					Merger.class
				);
			
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resObserver());

			return aggregateAndReturn(ae, Merger.TASK_NAME, 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	
	/**
	 * 
	 * Sets the {@link Term#setRank(int)} of all terms of the {@link TermIndex}
	 * given a {@link TermProperty}.
	 * 
	 * @param property
	 * @param desc
	 * @return
	 */
	public TermSuitePipeline aeRanker(TermProperty property, boolean desc)   {
		Preconditions.checkArgument(property != TermProperty.RANK, "Cannot rank on property %s", TermProperty.RANK);
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					Ranker.class,
					Ranker.RANKING_PROPERTY, property,	
					Ranker.DESC, desc
				);
				ExternalResourceFactory.bindResource(ae, resTermIndex());
				ExternalResourceFactory.bindResource(ae, resObserver());
				ExternalResourceFactory.bindResource(ae, resHistory());


			return aggregateAndReturn(ae, Ranker.TASK_NAME, 1);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	public TermSuitePipeline setTreeTaggerHome(String treeTaggerPath) {
		this.treeTaggerPath = Optional.of(treeTaggerPath);
		return this;
	}

	public TermSuitePipeline haeLogOverlappingRules() {
		this.logOverlappingRules = Optional.of(true);
		return this;
	}
	public TermSuitePipeline enableSyntacticLabels() {
		this.enableSyntacticLabels = true;
		return this;
	}
	
	public TermSuitePipeline setCompostCoeffs(float alpha, float beta, float gamma, float delta) {
		Preconditions.checkArgument(alpha + beta + gamma + delta == 1.0f, "The sum of coeff must be 1.0");
		this.alpha = Optional.of(alpha);
		this.beta = Optional.of(beta);
		this.gamma = Optional.of(gamma);
		this.delta = Optional.of(delta);
		return this;
	}
	
	public TermSuitePipeline setCompostMaxComponentNum(int compostMaxComponentNum) {
		this.compostMaxComponentNum = Optional.of(compostMaxComponentNum);
		return this;
	}
	
	public TermSuitePipeline setCompostMinComponentSize(int compostMinComponentSize) {
		this.compostMinComponentSize = Optional.of(compostMinComponentSize);
		return this;
	}
	
	public TermSuitePipeline setCompostScoreThreshold(float compostScoreThreshold) {
		this.compostScoreThreshold = Optional.of(compostScoreThreshold);
		return this;
	}
	
	public TermSuitePipeline setCompostSegmentSimilarityThreshold(
			float compostSegmentSimilarityThreshold) {
		this.compostSegmentSimilarityThreshold = Optional.of(compostSegmentSimilarityThreshold);
		return this;
	}
	
	public TermSuitePipeline aeCompostSplitter()  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					CompostAE.class,
					CompostAE.SCORE_THRESHOLD, this.compostScoreThreshold.isPresent() ? this.compostScoreThreshold.get() : this.lang.getCompostScoreThreshold(),
					CompostAE.ALPHA, alpha.isPresent() ? alpha.get() : lang.getCompostAlpha(),
					CompostAE.BETA, beta.isPresent() ? beta.get() : lang.getCompostBeta(),
					CompostAE.GAMMA, gamma.isPresent() ? gamma.get() : lang.getCompostGamma(),
					CompostAE.DELTA, delta.isPresent() ? delta.get() : lang.getCompostDelta(),
					CompostAE.MIN_COMPONENT_SIZE, this.compostMinComponentSize.isPresent() ? this.compostMinComponentSize.get() : this.lang.getCompostMinComponentSize(),
					CompostAE.MAX_NUMBER_OF_COMPONENTS, this.compostMaxComponentNum.isPresent() ? this.compostMaxComponentNum.get() : this.lang.getCompostMaxComponentNumber(),
					CompostAE.SEGMENT_SIMILARITY_THRESHOLD, this.compostSegmentSimilarityThreshold.get()
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.bindResource(ae, resObserver());
			
			
			ExternalResourceDescription langDicoRes = ExternalResourceFactory.createExternalResourceDescription(
					SimpleWordSet.class, 
					getResUrl(TermSuiteResource.DICO));
			
			ExternalResourceFactory.bindResource(
					ae,
					CompostAE.LANGUAGE_DICO, 
					langDicoRes
				);
			
			
			ExternalResourceDescription compostInflectionRulesRes = ExternalResourceFactory.createExternalResourceDescription(
					CompostInflectionRules.class, 
					getResUrl(TermSuiteResource.COMPOST_INFLECTION_RULES));
			
			ExternalResourceFactory.bindResource(
					ae,
					CompostAE.INFLECTION_RULES, 
					compostInflectionRulesRes
				);
			
			
			ExternalResourceDescription transformationRulesRes = ExternalResourceFactory.createExternalResourceDescription(
					CompostInflectionRules.class, 
					getResUrl(TermSuiteResource.COMPOST_TRANSFORMATION_RULES));
			
			ExternalResourceFactory.bindResource(
					ae,
					CompostAE.TRANSFORMATION_RULES, 
					transformationRulesRes
				);
			
			ExternalResourceDescription compostStopListRes = ExternalResourceFactory.createExternalResourceDescription(
					SimpleWordSet.class, 
					getResUrl(TermSuiteResource.COMPOST_STOP_LIST));
			
			ExternalResourceFactory.bindResource(
					ae,
					CompostAE.STOP_LIST, 
					compostStopListRes
				);
			
			
			ExternalResourceDescription neoClassicalPrefixesRes = ExternalResourceFactory.createExternalResourceDescription(
					SimpleWordSet.class, 
					getResUrl(TermSuiteResource.NEOCLASSICAL_PREFIXES));
			
			ExternalResourceFactory.bindResource(
					ae,
					CompostAE.NEOCLASSICAL_PREFIXES, 
					neoClassicalPrefixesRes
				);
			
			ExternalResourceFactory.bindResource(ae, resHistory());

			
			return aeManualCompositionSetter()
					.aggregateAndReturn(ae, CompostAE.TASK_NAME, 2);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}


	public TermSuitePipeline haeCasStatCounter(String statName)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					CasStatCounter.class,
					CasStatCounter.STAT_NAME, statName
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, getNumberedTaskName("Counting stats ["+statName+"]"), 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * 
	 * Exports time progress to TSV file.
	 * 
	 * Columns are :
	 * <ul>
	 * <li>elapsed time from initialization in milliseconds</li>
	 * <li>number of docs processed</li>
	 * <li>cumulated size of data processed</li>
	 * <li>number of terms in term index</li>
	 * <li>number of {@link WordAnnotation} processed</li>
	 * </ul>
	 * 
	 * 
	 * @param toFile
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeTraceTimePerf(String toFile)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					CasStatCounter.class,
					CasStatCounter.DOCUMENT_PERIOD, 1,
					CasStatCounter.TO_TRACE_FILE, toFile
				);
			ExternalResourceFactory.bindResource(ae, resTermIndex());

			return aggregateAndReturn(ae, "Exporting time performances to file " + toFile, 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}


	/**
	 * 
	 * @param refFileURI
	 * 			The path to reference termino
	 * @param outputFile
	 * 			The path to output log file
	 * @param customLogHeader
	 * 			A custom string to add in the header of the output log file
	 * @param rFile
	 * 			The path to output r file
	 * @param evalTraceName
	 * 			The name of the eval trace
	 * @param rtlWithVariants
	 * 			true if variants of the reference termino should be kept during the eval
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline haeEval(String refFileURI, String outputFile, String customLogHeader, String rFile, String evalTraceName, boolean rtlWithVariants)  {
		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
				EvalEngine.class,
				EvalEngine.OUTPUT_LOG_FILE, outputFile,
				EvalEngine.OUTPUT_R_FILE, rFile,
				EvalEngine.CUSTOM_LOG_HEADER_STRING, customLogHeader,
//				EvalEngine.LC_WITH_VARIANTS, extractedTerminoWithVariants,
				EvalEngine.RTL_WITH_VARIANTS, rtlWithVariants
				
			);
			ExternalResourceFactory.bindResource(ae, resTermIndex());
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					EvalEngine.EVAL_TRACE, 
					EvalTrace.class, 
					evalTraceName);
			ExternalResourceFactory.createDependencyAndBind(
					ae,
					EvalEngine.REFERENCE_LIST, 
					ReferenceTermList.class, 
					"file:" + refFileURI);

			return aggregateAndReturn(ae, "Evaluating " + evalTraceName, 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}
	
	/**
	 * 
	 * Stores occurrences to MongoDB
	 * 
	 * @param mongoDBUri
	 * 			the mongo db connection uri
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setMongoDBOccurrenceStore(String mongoDBUri) {
		this.occurrenceStore = new MongoDBOccurrenceStore(mongoDBUri);
		return this;
	}

	
	/**
	 * @deprecated Use TermSuitePipeline#setOccurrenceStoreMode instead.
	 * 
	 * @param activate
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 * 
	 */
	@Deprecated
	public TermSuitePipeline setSpotWithOccurrences(boolean activate) {
		this.spotWithOccurrences = activate;
		return this;
	}
	
	/**
	 * Configures {@link RegexSpotter}. If <code>true</code>, 
	 * adds all spotted occurrences to the {@link TermIndex}.
	 * 
	 * @see #aeRegexSpotter()
	 * 
	 * @param addToTermIndex
	 * 			the value of the parameter
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setAddSpottedAnnoToTermIndex(boolean addToTermIndex) {
		this.addSpottedAnnoToTermIndex = addToTermIndex;
		return this;
	}

	/**
	 * Sets the post processing strategy for {@link RegexSpotter} analysis engine
	 * 
	 * @see #aeRegexSpotter()
	 * @see OccurrenceBuffer#NO_CLEANING
	 * @see OccurrenceBuffer#KEEP_PREFIXES
	 * @see OccurrenceBuffer#KEEP_SUFFIXES
	 * 
	 * @param postProcessingStrategy
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setPostProcessingStrategy(
			String postProcessingStrategy) {
		
		this.postProcessingStrategy = Optional.of(postProcessingStrategy);
		
		return this;
	}
	
	/**
	 * Configures tsvExporter to (not) show headers on the 
	 * first line.
	 * 
	 * @param tsvWithHeaders
	 * 			the flag
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline setTsvShowHeaders(boolean tsvWithHeaders) {
		this.tsvWithHeaders = tsvWithHeaders;
		return this;
	}
	
	/**
	 * Configures tsvExporter to (not) show variant scores with the
	 * "V" label
	 * 
	 * @param tsvWithVariantScores
	 * 			the flag
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
		 */
	public TermSuitePipeline setTsvShowScores(boolean tsvWithVariantScores) {
		this.tsvWithVariantScores = tsvWithVariantScores;
		return this;
	}

	public TermSuitePipeline haeJsonCasExporter(String toDirectoryPath ) {

		try {
			AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(
					JsonCasExporter.class,
					JsonCasExporter.OUTPUT_DIRECTORY, toDirectoryPath
			);
			return aggregateAndReturn(ae, getNumberedTaskName("Exporting CAS to JSON files"), 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

	/**
	 * 
	 * Configures the {@link JsonExporterAE} to not embed the occurrences 
	 * in the json file, but to link the mongodb occurrence store instead.
	 * 
	 * 
	 * 
	 * @see #haeJsonExporter(String) 
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 */
	public TermSuitePipeline linkMongoStore() {
		this.linkMongoStore = true;
		return this;
	}
	

	/**
	 * 
	 * Aggregates an AE to the TS pipeline.
	 * 
	 * @param ae
	 * 			the ae description of the added pipeline.
	 * @param taskName
	 * 			a user-readable name for the AE task (intended to 
	 * 			be displayed in progress views)
	 * @return
	 * 		This chaining {@link TermSuitePipeline} builder object
	 * 			
	 */
	public TermSuitePipeline customAE(AnalysisEngineDescription ae, String taskName) {
		try {
			return aggregateAndReturn(ae, taskName, 0);
		} catch(Exception e) {
			throw new TermSuitePipelineException(e);
		}
	}

}