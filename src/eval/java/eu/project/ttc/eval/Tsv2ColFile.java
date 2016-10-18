package eu.project.ttc.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import eu.project.ttc.engines.cleaner.TermProperty;
import eu.project.ttc.models.Term;
import eu.project.ttc.models.TermIndex;
import eu.project.ttc.models.index.CustomTermIndex;
import eu.project.ttc.models.index.TermIndexes;

public class Tsv2ColFile {

	private static final Logger LOGGER = LoggerFactory.getLogger(Tsv2ColFile.class);
	
	private Path path;
	
	public Tsv2ColFile(Path path) {
		super();
		Preconditions.checkArgument(path.toFile().exists(), "File %s does not exist", path.toString());
		Preconditions.checkArgument(path.toFile().isFile(), "Not a file: %s", path.toString());
		this.path = path;
	}

	public List<String[]> getLines() throws IOException {
		return lines()
			.collect(Collectors.toList());
	}

	
	public Stream<Term[]> termPairs(TermIndex sourceTermino, TermIndex targetTermino) throws IOException {
		CustomTermIndex sourceLemmaIndex = sourceTermino.getCustomIndex(TermIndexes.LEMMA_LOWER_CASE);
		CustomTermIndex targetLemmaIndex = targetTermino.getCustomIndex(TermIndexes.LEMMA_LOWER_CASE);
		
		return lines().filter(line -> {
					if(!sourceLemmaIndex.containsKey(line[0])) {
						LOGGER.warn("Ignoring ref line <{}> (source term not found in source terminology)", Joiner.on(" ").join(line));
						return false;
					}
					if(!targetLemmaIndex.containsKey(line[1])) {
						LOGGER.warn("Ignoring ref line <{}> (target term not found in source terminology)", Joiner.on(" ").join(line));
						return false;
					}
					return true;
				}).map(line -> {
					List<Term> sources = sourceLemmaIndex.getTerms(line[0]);
					Collections.sort(sources, TermProperty.FREQUENCY.getComparator(true));
					List<Term> targets = targetLemmaIndex.getTerms(line[1]);
					Collections.sort(targets, TermProperty.FREQUENCY.getComparator(true));
					LOGGER.debug("Reading eval pair. Source: <{}>. Target: <{}>", sources.get(0), targets.get(0));

					return new Term[]{sources.get(0), targets.get(0)};
				});
	}


	public Stream<String[]> lines() throws IOException {
		return Files.lines(path)
			.map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
			.map(line -> line.trim())
			.filter(line -> !line.isEmpty())
			.map(line -> {
				List<String> list = Arrays.stream(line.split("\t")).map(col -> col.trim()).collect(Collectors.toList());
				return list.toArray(new String[list.size()]);
			})
			.filter(line -> {
				if(line.length < 2) {
					LOGGER.warn("Ignoring line <{}>. Only one column", Joiner.on('t').join(line));
					return false;
				} else 
					return true;
			})
			.map(line -> {
				if(line.length > 2) { 
					LOGGER.warn("Line <{}> has {} columns (only two columns required). Ignoring additional columns.", Joiner.on('t').join(line), line.length);
					return new String[] {line[0], line[1]};
				} else
					return line;
			});
	}
}
