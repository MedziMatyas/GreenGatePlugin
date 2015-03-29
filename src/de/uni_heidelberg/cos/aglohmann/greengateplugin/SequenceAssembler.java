package de.uni_heidelberg.cos.aglohmann.greengateplugin;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotationInterval;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;

import jebl.util.ProgressListener;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Matyas Medzihradszky on 29/01/15.
 */
public class SequenceAssembler {

    /**
     * This will be the final function to call to actually perform the GreenGate cloning, once the documents have been
     * selected and set up and options determined.
     * At this moment it is more a plan for the logic of how the whole thing will work.
     *
     * @param enzyme The enzyme used for the GreenGate reaction.
     * @param documents All the vectors that we use in the reaction.
     * @return The completed construct. (A list, but it should only have on element. A list is used as that is the final return type needed by the Document Operation).
     * @throws DocumentOperationException If any of the conversions or document operations used fail for some reason.
     */
    public List<AnnotatedPluginDocument> performCloning(Enzyme enzyme, List<AnnotatedPluginDocument> documents) throws DocumentOperationException {
        List<AnnotatedPluginDocument> results = new LinkedList<AnnotatedPluginDocument>() {};
        List<AnnotatedPluginDocument> workingDocuments = new LinkedList<AnnotatedPluginDocument>();

        //Cut all the inserts.
        for (AnnotatedPluginDocument doc : documents) {
            workingDocuments.addAll(selectNeededSequence(enzyme, cutSequence(enzyme, doc)));
        }

        //We pick the first resulting fragment as the one to start the reaction from.
        //This we will wish to change for error handling later on: ie. Which part works and which does not.
        AnnotatedPluginDocument destinationFragment = workingDocuments.remove(0);

        //TODO: How do we avoid an endless ligation?
        //Now I implemented the removal of each used fragment. Probably not good in all cases.
        AnnotatedPluginDocument match = findMatchingDocument(destinationFragment, workingDocuments);
        while (!((NucleotideSequenceDocument)destinationFragment.getDocument()).isCircular() && match != null) { //The second part will be null if there is no match, or the insert list is already empty.
            destinationFragment = ligateSequences(destinationFragment, match);
            workingDocuments.remove(match);
            match = findMatchingDocument(destinationFragment, workingDocuments);
        }
        if (!((NucleotideSequenceDocument)destinationFragment.getDocument()).isCircular()) {
            Dialogs.showMessageDialog("Could not circularise the plasmid. The resulting linear fragment is presented.");
            //TODO: Pop up an option to choose what to do.
        }
        results.add(cleanupAnnotations(destinationFragment));
        return results;
    }

    /**
     * Should convert a simple sequence document to a DefaultNucleotideSequence if possible. It will fail (returning
     * null) if the provided document is not a nucleotide sequence document of some kind.
     * (ie. Not an instanceof NucleotideSequenceDocument)
     *
     * @param document An AnnotatedPluginDocument to convert.
     * @return Returns a DefaultNucleotideSequence if successful, or null if not.
     * @throws DocumentOperationException If it cannot get the wrapped document for some reason, this is thrown.
     */
    private DefaultNucleotideSequence convertDocument(AnnotatedPluginDocument document) throws DocumentOperationException {
        if(document.getDocument() instanceof DefaultNucleotideSequence) {
            return (DefaultNucleotideSequence)document.getDocument();
        } else if (document.getDocument() instanceof NucleotideSequenceDocument) {
            NucleotideSequenceDocument seqDoc = (NucleotideSequenceDocument)document.getDocument();
            String seqString = seqDoc.getSequenceString();
            List<SequenceAnnotation> annotations = seqDoc.getSequenceAnnotations();

            DefaultNucleotideSequence nucSeq = new DefaultNucleotideSequence(document.getName(), seqString);
            nucSeq.setAnnotations(annotations);
            nucSeq.setCircular(seqDoc.isCircular());
            return nucSeq;
        } else {
            return null; //This is only returned if the sequence is not a nucleotide sequence. This on the other hand should be tested way before this step.
        }
    }

    /**
     * Performs a theoretical digestion on a given nucleotide sequence with an enzyme.
     *
     * @param enzyme The enzyme used for the digestion.
     * @param document The document containing a nucleotide sequence.
     * @return A list of documents that result from the digestion.
     * @throws DocumentOperationException
     */
    private List<AnnotatedPluginDocument> cutSequence(Enzyme enzyme, AnnotatedPluginDocument document) throws DocumentOperationException {
        //Retrieve the inner nucleotide sequence document and return null if this fails.
        DefaultNucleotideSequence nucleotideSequence;
        nucleotideSequence = convertDocument(document);

        //Clear any restriction site annotations already in the sequence to make sure it is cut in the right places.
        //The rest of the sequence annotations are not affected.
        List<SequenceAnnotation> annotations = nucleotideSequence.getSequenceAnnotations();
        List<SequenceAnnotation> toRemove = SequenceUtilities.getAnnotationsOfType(annotations, SequenceAnnotation.TYPE_RESTRICTION_SITE);
        annotations.removeAll(toRemove);

        //Find and enter the needed restriction site annotations, based on the selected enzyme.
        //This should also be possible to do with a built in DocumentOperation, but I am not sure how to set the needed
        //+options for it.
        String sequence = nucleotideSequence.getSequenceString();
        sequence.toUpperCase(); //Change to upper case to make sure matching works well.
        int index = 0;
        do {
            index = sequence.indexOf(enzyme.recognitionSite(), index);
            if (index > 0) {
                SequenceAnnotation anno = new SequenceAnnotation(enzyme.dispName(), SequenceAnnotation.TYPE_RESTRICTION_SITE);
                anno.addQualifier("Recognition pattern", enzyme.recognitionPattern());
                anno.addInterval(index + 1, index + enzyme.recognitionSite().length(), SequenceAnnotationInterval.Direction.leftToRight);
                annotations.add(anno);
                index += 1;
            }
        } while (index > 0);
        index = 0; //Reset counter to next pass... looking for backward recognition sites. Needed because GreenGate
                   //+enzyme recognition sites are not palindromic.
        do {
            index = sequence.indexOf(enzyme.revRecSite(), index);
            if (index > 0) {
                SequenceAnnotation anno = new SequenceAnnotation(enzyme.dispName(), SequenceAnnotation.TYPE_RESTRICTION_SITE);
                anno.addQualifier("Recognition pattern", enzyme.recognitionPattern());
                anno.addInterval(index + 1, index + enzyme.recognitionSite().length(), SequenceAnnotationInterval.Direction.rightToLeft);
                annotations.add(anno);
                index += 1;
            }
        } while (index > 0);

        nucleotideSequence.setAnnotations(annotations); //Add the new annotations to the nucleotide sequence.
        final AnnotatedPluginDocument digestDocument = DocumentUtilities.createAnnotatedPluginDocument(nucleotideSequence);
        //Perform digestion
        //Needed to get around AWT thread problems
        final DocumentOperation digest = PluginUtilities.getDocumentOperation("com.biomatters.plugins.restrictionAnalysis.DigestOperation");
        final AtomicReference<Options> options = new AtomicReference<Options>();
        ThreadUtilities.invokeNowOrWait(new Runnable() {
            public void run() {
                try {
                    options.set(digest.getOptions(digestDocument));
                } catch (DocumentOperationException e) {
                    e.printStackTrace();
                }
            }
        });
        //Dialogs.showMessageDialog(options.get().getDescriptionAndState()); //For debugging and testing purposes.
        return digest.performOperation(ProgressListener.EMPTY, options.get(), digestDocument); //returns a DefaultSequenceListDocument

    }

    /**
     * Select the needed sequence fragment based on the enzyme used and whether the fragments are from the destination
     * vector or a simple insert vector.
     */
    private List<AnnotatedPluginDocument> selectNeededSequence(Enzyme enzyme, List<AnnotatedPluginDocument> documents) throws DocumentOperationException {
        //Set up restriction site annotation to search for
        SequenceAnnotation recognitionSite = new SequenceAnnotation(enzyme.dispName(), SequenceAnnotation.TYPE_RESTRICTION_SITE);

        //Where we gather the sequences to check. Needed because the supplied PluginDocuments might be sequence lists.
        List<NucleotideSequenceDocument> nucleotideSequences = new LinkedList<NucleotideSequenceDocument>();

        for (AnnotatedPluginDocument doc : documents) {
            if (doc.getDocument() instanceof com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument) {
                DefaultSequenceListDocument docList = (DefaultSequenceListDocument)doc.getDocument();
                nucleotideSequences.addAll(docList.getNucleotideSequences());
            } else if (doc.getDocument() instanceof com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument) {
                nucleotideSequences.add((NucleotideSequenceDocument)doc.getDocument());
            } else {
                return null; //Do this if we cannot correctly cast the received sequences. Should not happen.
            }
        }

        //Go through all the sequences looking for enzyme recognition sites.
        //Remove those that have the sites. We need those that do not contain them.
        for(Iterator<NucleotideSequenceDocument> iterator = nucleotideSequences.iterator(); iterator.hasNext();) {
            NucleotideSequenceDocument document = iterator.next();
            List<SequenceAnnotation> annotations = document.getSequenceAnnotations();
            if (containsAnnotation(annotations, recognitionSite)) {
                iterator.remove();
            }
        }
        List<AnnotatedPluginDocument> resultingDocuments = DocumentUtilities.createAnnotatedPluginDocuments(nucleotideSequences); //Convert the nucleotide sequences back to AnnotatedPluginDocuments.
        return resultingDocuments; //Should in most cases be a single document, but just in case.
    }

    /**
     * Checks if a sequence contains an annotation with the same type and name as a reference annotation.
     * Used to check if a sequence contains a (annotated) restriction site, for example, anywhere on the sequence.
     *
     * @param annotations The list of SequenceAnnotations to search through.
     * @param annotationToFind The reference annotation.
     * @return True if found, false otherwise. Does not check how many have been found, just if it is there or not.
     */
    private boolean containsAnnotation(List<SequenceAnnotation> annotations, SequenceAnnotation annotationToFind) {
        annotations = SequenceUtilities.getAnnotationsOfType(annotations, annotationToFind.getType());
        if (annotations.isEmpty()) {
            return false;
        } else {
            for (SequenceAnnotation annotation : annotations) {
                if (annotation.getName().equalsIgnoreCase(annotationToFind.getName())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Ligates two selected sequences together, by calling the builtin ligation operation. Should return a single linear
     * or circular sequence, or an empty list if ligation cannot happen because of incompatible ends.
     * Circularises sequences by default if possible.
     *
     * @param destination
     * @param insert
     * @return
     * @throws DocumentOperationException
     */
    private AnnotatedPluginDocument ligateSequences(AnnotatedPluginDocument destination, AnnotatedPluginDocument insert) throws DocumentOperationException {
        final AnnotatedPluginDocument[] documents = new AnnotatedPluginDocument[] {destination, insert};
        //Perform ligation
        //Needed to get around AWT thread problems
        final DocumentOperation ligate = PluginUtilities.getDocumentOperation("com.biomatters.plugins.cloning.LigateOperation");
        final AtomicReference<Options> options = new AtomicReference<Options>();
        ThreadUtilities.invokeNowOrWait(new Runnable() {
            public void run() {
                try {
                    options.set(ligate.getOptions(documents));
                } catch (DocumentOperationException e) {
                    e.printStackTrace();
                }
            }
        });
        if(isDoubleMatch(destination, insert)) {
            options.get().getOption("circular").setValue(true);
        } else {
            options.get().getOption("circular").setValue(false);
        }
        //Dialogs.showMessageDialog(options.get().getDescriptionAndState());
        List<AnnotatedPluginDocument> results = ligate.performOperation(documents, ProgressListener.EMPTY, options.get());
        if (results.size() > 1) { //The ligation did not happen for some reason.
            return null;
        }
        return results.get(0);
    }

    /**
     * Checks whether a list of plugin documents contains a document with matching overhangs to the target document. It
     * also checks whether the overhangs are compatible only on one side, or two.
     *
     * @param destination The sequence we are trying to find a match to.
     * @param inserts The sequences in which we are looking for a match.
     * @return The first matching document it finds, or null if either there are no matching documents found, or the destination fragment is circular.
     * @throws DocumentOperationException
     */
    private AnnotatedPluginDocument findMatchingDocument(AnnotatedPluginDocument destination, List<AnnotatedPluginDocument> inserts) throws DocumentOperationException {
        if (((NucleotideSequenceDocument)destination.getDocument()).isCircular()) {
            return null; //Cannot find anything as we cannot ligate into a circular DNA.
        }
        List<SequenceAnnotation> destinationAnnotations = getFlankingOverhangs(destination);
        for(AnnotatedPluginDocument insert : inserts) {
            List<SequenceAnnotation> insertAnnotations = getFlankingOverhangs(insert);
            for (SequenceAnnotation insAnnotation : insertAnnotations) {
                for (SequenceAnnotation destAnnotation : destinationAnnotations) {
                    if (insAnnotation.getQualifierValue("sequence").equalsIgnoreCase(destAnnotation.getQualifierValue("sequence"))
                        && insAnnotation.getInterval().getDirection() != destAnnotation.getInterval().getDirection()) {
                        return insert;
                    }
                }
            }
        }
        return null; //If nothings is found.
    }

    /**
     * Find if the two selected documents can be ligated together at both sides. This is considered true, when the
     * +overhangs at both ends are compatible with each other.
     *
     * @param destination
     * @param insert
     * @return True if the ligation of the sequences forms a circular sequence. False otherwise.
     * @throws DocumentOperationException
     */
    private boolean isDoubleMatch(AnnotatedPluginDocument destination, AnnotatedPluginDocument insert) throws DocumentOperationException {
        List<SequenceAnnotation> destinationAnnotations = getFlankingOverhangs(destination);
        List<SequenceAnnotation> insertAnnotations = getFlankingOverhangs(insert);
        //Both lists will have a size of 2
        LinkedList<SequenceAnnotation> matchingOverhangs = new LinkedList<SequenceAnnotation>();
        for (SequenceAnnotation dAnno : destinationAnnotations) {
            for (SequenceAnnotation iAnno : insertAnnotations) {
                if (iAnno.getQualifierValue("sequence").equalsIgnoreCase(dAnno.getQualifierValue("sequence"))
                        && iAnno.getInterval().getDirection() != dAnno.getInterval().getDirection()) {
                    matchingOverhangs.add(iAnno);
                    matchingOverhangs.add(dAnno);
                }
            }
        }
        //If there is a double match, all four flanking annotations should have been added to the list.
        if (matchingOverhangs.containsAll(destinationAnnotations) && matchingOverhangs.containsAll(insertAnnotations)) {
            return true;
        }
        return false;
    }

    /**
     * Gets the two overhangs that flank the sequence.
     * This is needed as a file can actually contain overhang annotations in the middle of the sequence as well.
     *
     * @param document
     * @return
     * @throws DocumentOperationException
     */
    private List<SequenceAnnotation> getFlankingOverhangs(AnnotatedPluginDocument document) throws DocumentOperationException {
        List<SequenceAnnotation> annotations = ((NucleotideSequenceDocument)document.getDocument()).getSequenceAnnotations();
        String sequence = ((NucleotideSequenceDocument)document.getDocument()).getSequenceString();
        int sequenceLength = ((NucleotideSequenceDocument)document.getDocument()).getSequenceLength();

        annotations = SequenceUtilities.getAnnotationsOfType(annotations, SequenceAnnotation.TYPE_OVERHANG);

        List<SequenceAnnotation> flankingOverhangs = new LinkedList<SequenceAnnotation>() {};
        //TODO: Check if these are really stored this way. <- Seem to be.
        for (SequenceAnnotation anno : annotations) {
            SequenceAnnotationInterval overhangToAdd = null; //Reset for each annotation.
            boolean isAtSequenceStart = true;
            List<SequenceAnnotationInterval> intervals = anno.getIntervals();
            for (SequenceAnnotationInterval interval : intervals) {
                //Dialogs.showMessageDialog("Found interval = " + interval.getDirection() + ", " + interval.getLength() + ", " + interval.getFrom() + "\nSequence length = " + sequenceLength);
                if (interval.getFrom() == 1 && interval.getDirection().isDirectedRight()) {
                    overhangToAdd = interval;
                }
                if (interval.getFrom() == sequenceLength && interval.getDirection().isDirectedLeft()) {
                    overhangToAdd = interval;
                    isAtSequenceStart = false;
                }
                if (interval.getFrom() == sequenceLength - interval.getLength() + 1 && interval.getDirection().isDirectedRight()) {
                    overhangToAdd = interval;
                    isAtSequenceStart = false;
                }
                if (interval.getFrom() == interval.getLength() && interval.getDirection().isDirectedLeft()) {
                    overhangToAdd = interval;
                }
            }
            if (overhangToAdd != null) { //So we found something.
                String overhangSequence;
                List<SequenceAnnotationInterval> overhangIntervals = new LinkedList<SequenceAnnotationInterval>();
                if (isAtSequenceStart) {
                    overhangSequence = sequence.substring(0, overhangToAdd.getLength());
                } else {
                    overhangSequence = sequence.substring(sequenceLength - overhangToAdd.getLength());
                }
                while (!anno.getQualifierValue("sequence").equalsIgnoreCase("")) { //Make sure there are no earlier, or other such qualifiers on this annotations.
                    anno.removeQualifier("sequence");
                }
                anno.addQualifier("sequence", overhangSequence);
                overhangIntervals.add(overhangToAdd);
                anno.setIntervals(overhangIntervals); //Make sure it only has one interval associated with it.
                flankingOverhangs.add(anno);
                //Dialogs.showMessageDialog("Found annotation to add:\n" + anno.getQualifierValue("Bases"));
            }
        }
        if (flankingOverhangs.toArray().length != 2) {
            Dialogs.showMessageDialog("Error if finding flanking overhangs for " + document.getName() + "\nFound " + flankingOverhangs.toArray().length + "intervals");
            return null; //Somethings is wrong as there should always be two flanking overhangs in case of a cut sequence.
        }
        return flankingOverhangs;
    }

    /**
     *
     * @param inputDocument
     * @return
     */
    private AnnotatedPluginDocument cleanupAnnotations(AnnotatedPluginDocument inputDocument) {
        List<SequenceAnnotation> annotations = null;
        DefaultNucleotideSequence outputDocument = null;
        try {
            outputDocument = convertDocument(inputDocument);
            annotations = outputDocument.getSequenceAnnotations();
        } catch (DocumentOperationException e) {
            e.printStackTrace();
            return null;
        }
        List<SequenceAnnotation> toRemove;
        toRemove = SequenceUtilities.getAnnotationsOfType(annotations, SequenceAnnotation.TYPE_CONCATENATED_SEQUENCE);
        annotations.removeAll(toRemove);
        outputDocument.setAnnotations(annotations);
        outputDocument.setDescription(""); //Does this work?
        return DocumentUtilities.createAnnotatedPluginDocument(outputDocument);
    }

    /**
     *
     * @param inputDocument
     * @return
     */
    private AnnotatedPluginDocument recalculateIntervals(AnnotatedPluginDocument inputDocument) {
        List<SequenceAnnotation> annotations = null;
        DefaultNucleotideSequence outputDocument = null;
        try {
            outputDocument = convertDocument(inputDocument);
            annotations = outputDocument.getSequenceAnnotations();
        } catch (DocumentOperationException e) {
            e.printStackTrace();
            return null;
        }
        int sequenceLength = outputDocument.getSequenceString().length();
        return null;
    }

    private Enzyme enzyme; //Not used yet as only a single enzyme is supplied.
}
