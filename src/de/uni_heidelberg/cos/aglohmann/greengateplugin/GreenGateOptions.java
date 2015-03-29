package de.uni_heidelberg.cos.aglohmann.greengateplugin;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.virion.jam.util.SimpleListener;

import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * This is the main dialog (GUI) where all the files are entered and options set up.
 *
 * Created by matyas on 09/03/15.
 */
public class GreenGateOptions extends Options implements ActionListener {

    public GreenGateOptions(AnnotatedPluginDocument... documents) {
        //Initialize the arrays
        workingDocuments = new AnnotatedPluginDocument[numEntries];
        vectorNames = new StringOption[numEntries];
        deleteThis = new ButtonOption[numEntries];

        //Fill up the document storage array
        for (int i = 0; i < numEntries; i++) {
            if (i < documents.length) { //We still have documents to work with
                workingDocuments[i] = documents[i];
            } else {
                workingDocuments[i] = null;
            }
        }

        //Setup view and default values.
        //Top labels
        //TODO: These need work to look good, but that is just cosmetic.
        beginAlignHorizontally(null, false);
        this.addLabel("File list", true, false);
        endAlignHorizontally();

        //File list
        for (int i = 0; i < numEntries; i++) {
            beginAlignHorizontally(null, false);
            this.addLabel("   ");
            vectorNames[i] = addStringOption("vector:" + i,"" , "", "No file selected");
            deleteThis[i] = addButtonOption("delete:" + i, "", "X");
            endAlignHorizontally();
        }

        //Document selections
        beginAlignHorizontally(null, false);
        getAdditionalDocuments = addDocumentSelectionOption("getDocument", "", DocumentSelectionOption.FolderOrDocuments.EMPTY, DocumentType.NUCLEOTIDE_SEQUENCE_TYPE, null, true, null);
        addDocument = addButtonOption("addDocument", "", "Add sequences");
        endAlignHorizontally();

        //Setup listeners for changes and actions
        for (int i = 0; i < numEntries; i++) {
            deleteThis[i].addActionListener(this);
        }
        addDocument.addActionListener(this);

        //Fill in the vector names that are already selected
        setVectorNames();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        //Dialogs.showMessageDialog("Found action event");
        Options.ButtonOption actionSource = (Options.ButtonOption)((JPanel)actionEvent.getSource()).getClientProperty(Options.ASSOCIATED_OPTION_PROPERTY);
        String[] toParse = actionSource.getName().split(":");
        if (toParse[0].equalsIgnoreCase("delete")) {
            int numSource = Integer.decode(toParse[1]);
            deleteDocument(numSource);
        } else if (toParse[0].equalsIgnoreCase("addDocument")) {
            addDocument();
        }

    }

    private void setVectorNames() {
        for (int i = 0; i < numEntries; i++) {
            if (workingDocuments[i] != null) {
                vectorNames[i].setValue(workingDocuments[i].getName());
            } else {
                vectorNames[i].setValue("");
            }
        }
    }

    /**
     * Deletes the document stored at the selected index and updates the array for storing the documents as well as the
     * +displayed names.
     *
     * @param index The index in the array of the document to be deleted.
     */
    private void deleteDocument(int index) {
        workingDocuments[index] = null;
        for (int i = index; i < numEntries; i++) {
            if (i != numEntries - 1) { //We are at the last position in the array
                workingDocuments[i] = workingDocuments[i + 1];
            } else {
                workingDocuments[i] = null;
            }

        }
        setVectorNames();
    }

    /**
     * Gets the number of documents stored.
     *
     * @return
     */
    private int getNumCurrent() {
        for (int i = 0; i < numEntries; i++) {
            if (workingDocuments[i] == null) {
                return i;
            }
        }
        return numEntries;
    }

    /**
     * Gets all the actually stored documents.
     *
     * @return
     */
    public AnnotatedPluginDocument[] getWorkingDocuments() {
        int size = getNumCurrent();
        AnnotatedPluginDocument[] results = new AnnotatedPluginDocument[size];
        for (int i = 0; i < size; i++) {
            results[i] = workingDocuments[i];
        }
        return results;
    }

    @Override
    public boolean areValuesGoodEnoughToContinue() {
        if (!super.areValuesGoodEnoughToContinue()) {
            return false;
        }
        if (getNumCurrent() < 2) {
            Dialogs.showMessageDialog("You need to select at least two sequences.");
            return false;
        }
        for (AnnotatedPluginDocument doc : getWorkingDocuments()) {
            try {
                if (!(doc.getDocument() instanceof NucleotideSequenceDocument)) {
                    Dialogs.showMessageDialog(doc.getName() + " is not a nucleotide sequence!");
                    return false;
                }
            } catch (DocumentOperationException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog("Debug message:\nError: could not get document from AnnotatedPluginDocument at areValuesGoodEnoughToContinue()");
                return false;
            }
        }
        return true;
    }

    private boolean addDocument() {
        List<AnnotatedPluginDocument> addedDocuments = getAdditionalDocuments.getDocuments();
        if (!addedDocuments.isEmpty()) {
            int i = 0;
            int current = getNumCurrent();
            if (current + addedDocuments.size() > workingDocuments.length) {
                Dialogs.showMessageDialog("Too many documents selected.\nOnly those added that fit.");
                return false;
            } else {
                for (AnnotatedPluginDocument doc : addedDocuments) {
                    workingDocuments[current + i] = doc;
                }
            }
        }
        setVectorNames();
        return true;
    }

    //The max number of entries the program handles. Determined arbitrarily.
    private static final int numEntries = 7;

    private StringOption[] vectorNames;
    private ButtonOption[] deleteThis;
    private ButtonOption addDocument;
    private AnnotatedPluginDocument[] workingDocuments;
    private DocumentSelectionOption getAdditionalDocuments;
}
