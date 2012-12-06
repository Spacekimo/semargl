/*
 * Copyright 2012 Lev Khomich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.semarglproject.rdf.rdfa;

import org.semarglproject.rdf.ParseException;
import org.semarglproject.ri.CURIE;
import org.semarglproject.ri.IRI;
import org.semarglproject.ri.MalformedCURIEException;
import org.semarglproject.ri.MalformedIRIException;
import org.semarglproject.vocab.RDFa;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EvalContext {

    private final ResourceResolver resolver;

    public final Map<String, String> iriMappings;
    public String subject;
    public String object;
    public List<Object> incomplTriples;
    public String lang;
    public String objectLit;
    public String objectLitDt;
    public String properties;
    public String vocab;
    public String profile;
    public boolean parsingLiteral;
    public Map<String, List<Object>> listMapping;

    public EvalContext(ResourceResolver resolver, String base) {
        // RDFa Core 1.0 processing sequence step 1
        this(null, null, null, resolver);
        this.subject = base;
    }

    private EvalContext(String lang, String vocab, String profile, ResourceResolver resolver) {
        this.subject = null;
        this.object = null;
        this.iriMappings = new HashMap<String, String>();
        this.incomplTriples = new ArrayList<Object>();
        this.lang = lang;
        this.objectLit = null;
        this.objectLitDt = null;
        this.vocab = vocab;
        this.profile = profile;
        this.properties = null;
        this.parsingLiteral = false;
        this.listMapping = new HashMap<String, List<Object>>();
        this.resolver = resolver;
    }

    public EvalContext initChildContext(String profile, String vocab, String lang,
                                        Map<String, String> overwriteMappings, short rdfaVersion) {
        // RDFa Core 1.0 processing sequence step 2
        EvalContext current = new EvalContext(this.lang, this.vocab, this.profile, resolver);
        current.listMapping = listMapping;
        current.iriMappings.putAll(iriMappings);
        current.iriMappings.putAll(overwriteMappings);

        if (rdfaVersion > RDFa.VERSION_10) {
            if (profile != null) {
                String newProfile = profile + "#";
                if (current.profile == null) {
                    current.profile = newProfile;
                } else {
                    current.profile = newProfile + ' ' + current.profile;
                }
            }
            if (vocab != null) {
                current.vocab = vocab;
                if (current.vocab.length() == 0) {
                    current.vocab = null;
                } else {
                    resolver.declareVocabulary(current.vocab);
                }
            }
        }

        // RDFa Core 1.0 processing sequence step 3
        if (lang != null) {
            current.lang = lang;
        }
        if (current.lang != null && current.lang.isEmpty()) {
            current.lang = null;
        }
        return current;
    }

    public List<Object> getMappingForIri(String iri) {
        if (!listMapping.containsKey(iri)) {
            listMapping.put(iri, new ArrayList<Object>());
        }
        return listMapping.get(iri);
    }

    public void addContent(String content) {
        objectLit += content;
    }

    public void setObjectLit(String objectLit) {
        this.objectLit = objectLit;
    }

    public void updateBase(String oldBase, String base) {
        if (object != null && object.equals(oldBase)) {
            object = base;
        }
        if (subject != null && subject.equals(oldBase)) {
            subject = base;
        }
    }

    /**
     * Resolves @predicate or @datatype according to RDFa Core 1.1 section 5
     *
     * @param value value of attribute
     * @param rdfaVersion version of RDFa
     * @return resource IRI
     * @throws org.xml.sax.SAXException wrapped ParseException in case if IRI can not be resolved
     */
    public String resolvePredOrDatatype(String value, short rdfaVersion) throws SAXException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            if (rdfaVersion > RDFa.VERSION_10) {
                return resolveTermOrSafeCURIEOrAbsIri(value, rdfaVersion);
            } else {
                return resolveTermOrSafeCURIE(value, rdfaVersion);
            }
        } catch (MalformedIRIException e) {
            throw new SAXException(new ParseException(e));
        }
    }

    /**
     * Resolves @about or @resource according to RDFa Core 1.1 section 5
     *
     * @param value value of attribute
     * @param rdfaVersion version of RDFa
     * @return resource IRI
     * @throws org.semarglproject.rdf.ParseException if IRI can not be resolved
     */
    public String resolveAboutOrResource(String value, short rdfaVersion) throws ParseException {
        try {
            String result = resolver.resolveBNode(value);
            if (result == null) {
                try {
                    String iri = CURIE.resolve(value, iriMappings, rdfaVersion > RDFa.VERSION_10);
                    result = resolver.resolveIri(iri);
                } catch (MalformedCURIEException e) {
//                    resolver.warning(RDFa.UNRESOLVED_CURIE);
                }
            }
            if (result == null) {
                result = resolver.resolveIri(value);
            }
            return result;
        } catch (MalformedIRIException e) {
            throw new ParseException(e);
        }
    }

    /**
     * Resolves TERMorCURIEorAbsIRI according to RDFa Core 1.1 section A
     * @param value value to be resolved
     * @param rdfaVersion version of RDFa
     * @return resource IRI
     * @throws org.semarglproject.ri.MalformedIRIException
     */
    private String resolveTermOrSafeCURIEOrAbsIri(String value, short rdfaVersion)
            throws MalformedIRIException {
        if (IRI.isAbsolute(value)) {
            return value;
        }
        return resolveTermOrSafeCURIE(value, rdfaVersion);
    }

    /**
     * Resolves TERMorSafeCURIE according to RDFa Core 1.1 section A
     * @param value value to be resolved
     * @param rdfaVersion version of RDFa
     * @return resource IRI
     * @throws org.semarglproject.ri.MalformedIRIException
     */
    private String resolveTermOrSafeCURIE(String value, short rdfaVersion)
            throws MalformedIRIException {
        if (vocab != null && value.matches("[a-zA-Z0-9]+")) {
            return resolver.resolveIri( vocab + value);
        }
        if (value.indexOf(':') == -1) {
            String result = CURIE.resolveXhtmlTerm(value);
            if (rdfaVersion == RDFa.VERSION_10) {
                return result;
            }
            if (result == null) {
                result = CURIE.resolvePowderTerm(value);
                if (result == null) {
//                    resolver.warning(RDFa.UNRESOLVED_TERM);
                }
            }
            return result;
        }
        try {
            String iri = CURIE.resolve(value, iriMappings, rdfaVersion > RDFa.VERSION_10);
            return resolver.resolveIri(iri);
        } catch (MalformedCURIEException e) {
//            resolver.warning(RDFa.UNRESOLVED_CURIE);
            return null;
        }
    }

}
