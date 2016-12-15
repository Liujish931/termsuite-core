package fr.univnantes.termsuite.framework;

import java.util.Comparator;
import java.util.function.Predicate;

import com.google.common.collect.Ordering;

import fr.univnantes.termsuite.metrics.HarmonicMean;
import fr.univnantes.termsuite.model.RelationProperty;
import fr.univnantes.termsuite.model.TermRelation;

public class Relations {
	
	public static Comparator<TermRelation> relFreqHmean() {
		final HarmonicMean harmonicMean = new HarmonicMean();
		return Ordering
				.natural()
				.reverse()
				.onResultOf(r ->
					harmonicMean.mean(r.getFrom().getFrequency(), r.getTo().getFrequency())
				);
	}
	
	private static class BooleanPropertyPredicate implements Predicate<TermRelation> {
		private RelationProperty property;
		
		public BooleanPropertyPredicate(RelationProperty property) {
			super();
			this.property = property;
		}

		@Override
		public boolean test(TermRelation t) {
			return t.isPropertySet(property)
					&& t.getPropertyBooleanValue(property);
		}
	}

	public static Predicate<TermRelation> IS_MORPHOLOGICAL = new BooleanPropertyPredicate(RelationProperty.IS_MORPHOLOGICAL);
	public static Predicate<TermRelation> IS_DERIVATION = new BooleanPropertyPredicate(RelationProperty.IS_DERIVATION);
	public static Predicate<TermRelation> IS_PREFIXATION = new BooleanPropertyPredicate(RelationProperty.IS_PREXATION);
	public static Predicate<TermRelation> IS_SYNTAGMATIC = new BooleanPropertyPredicate(RelationProperty.IS_SYNTAGMATIC);
	public static Predicate<TermRelation> IS_GRAPHICAL = new BooleanPropertyPredicate(RelationProperty.IS_GRAPHICAL);
	public static Predicate<TermRelation> IS_SEMANTIC = new BooleanPropertyPredicate(RelationProperty.IS_SEMANTIC);
	public static Predicate<TermRelation> IS_INFERENCE = new BooleanPropertyPredicate(RelationProperty.IS_INFERED);
			

}
