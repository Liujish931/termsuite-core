
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

package fr.univnantes.termsuite.uima.readers;

import java.io.IOException;
import java.io.Writer;

import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import fr.univnantes.termsuite.types.WordAnnotation;

/**
 * 
 * Exports all {@link WordAnnotation} of a CAS to a {@link Writer}
 * 
 * @author Damien Cram
 *
 */
public class TSVCasSerializer {

    public static void serialize(JCas cas, Writer writer) throws IOException {
		AnnotationIndex<Annotation> index = cas
				.getAnnotationIndex(WordAnnotation.type);
		WordAnnotation word;
		for (Annotation annot : index) {
			word = (WordAnnotation) annot;
			writer.append(word.getCoveredText()).append('\t');
			writer.append(word.getCategory()).append('\t');
			writer.append(word.getLemma()).append('\n');
		}
        writer.close();
    }
}
