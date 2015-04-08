package de.uni_heidelberg.cos.aglohmann.greengateplugin;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;

import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import jebl.util.ProgressListener;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Matyas Medzihradszky
 * @version 2015-01-26.
 */

public class GreenGatePlugin extends GeneiousPlugin {
    public GreenGatePlugin() {
        super();
        getIconFile();
    }

    public String getName() {
        return "GreenGate Cloner";
    }

    public String getDescription() {
        return "Performs GreenGate cloning on selected vectors";
    }

    public String getHelp() {
        return null;
    }

    public String getAuthors() {
        return "Matyas Medzihradszky";
    }

    public String getVersion() {
        return "0.11";
    }

    public String getMinimumApiVersion() {
        return "4.0";
    } //Not sure if correct. Copied from one of the examples.

    public int getMaximumApiVersion() {
        return 4;
    } //Not sure if correct. Copied from one of the examples.

    public DocumentOperation[] getDocumentOperations() {
        return new DocumentOperation[]{greenGate};
    }

    private void getIconFile() {
        ggIcon = IconUtilities.getIconsFromJar(GreenGatePlugin.class, "/ggcloning24.png");
    }

    private DocumentOperation greenGate = new DocumentOperation() {

        public GeneiousActionOptions getActionOptions() {
            return new GeneiousActionOptions("GreenGate", "", ggIcon).setMainMenuLocation(GeneiousActionOptions.MainMenu.Tools).setInMainToolbar(true).setInPopupMenu(true);
        }

        public String getHelp() {
            return "Constructs a plasmid using green gate cloning from the supplied starting vectors.";
        }

        public DocumentSelectionSignature[] getSelectionSignatures() {
            return new DocumentSelectionSignature[]{DocumentSelectionSignature.forNucleotideSequences(1, Integer.MAX_VALUE, false)}; //Exactly used documents will be selected through Options.
        }

        //Opens a JPanel where the needed plasmids can be entered and options set.
        public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
            GGOptions = new GreenGateOptions(documents);
            return GGOptions;
        }

        //All of the operation logic is called from here. Options, and the selected documents are passed in.
        public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] docs, ProgressListener progress, Options options) {
            seqAssembler = new SequenceAssembler();
            AnnotatedPluginDocument[] vectors = GGOptions.getWorkingDocuments();
            Enzyme digestionEnzyme = GGOptions.getEnzyme();
            List<AnnotatedPluginDocument> results = new LinkedList<AnnotatedPluginDocument>();
            try {
                results = seqAssembler.performCloning(digestionEnzyme, Arrays.asList(vectors));
            } catch (DocumentOperationException e) {
                Dialogs.showMessageDialog("GreenGate cloning failed.\n" + "Error:\n" + e.getMessage());
                e.printStackTrace();
            }
            return results;
        }
    };

    private SequenceAssembler seqAssembler;
    private GreenGateOptions GGOptions;
    private Icons ggIcon;
}