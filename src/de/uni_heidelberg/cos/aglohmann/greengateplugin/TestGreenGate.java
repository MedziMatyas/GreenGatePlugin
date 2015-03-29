package de.uni_heidelberg.cos.aglohmann.greengateplugin;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;

/**
 * Created by matyas on 09/03/15.
 */
public class TestGreenGate {
    /**
     * Testing function. Pops up a message with all the available DocumentOperations available in Geneious.
     */
    public void findDocumentOperations() {
        StringBuilder builder = new StringBuilder();
        builder.append("Document Operations:\n");
        for (DocumentOperation operation : PluginUtilities.getDocumentOperations()) {
            builder.append(operation.getActionOptions().getName()+" has id: "+operation.getUniqueId()+"\n");
        }
//        builder.append("\n\nAnnotation Generators:\n");
//        for (SequenceAnnotationGenerator generator : PluginUtilities.getSequenceAnnotationGenerators()) {
//            builder.append(generator.getActionOptions().getName()+" has id: "+generator.getUniqueId()+"\n");
//        }
        String message=builder.toString();
        Dialogs.showMessageDialog(message);
    }

    /**
     * Testing function, trying to find the possible options that can be set for a given operation.
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
     */
    public void findOptions() throws DocumentOperationException {
        StringBuilder builder = new StringBuilder();
        builder.append("Options:\n");
        DocumentOperation digest = PluginUtilities.getDocumentOperation("com.biomatters.plugins.restrictionAnalysis.DigestOperation");
        for (Options.Option option : digest.getGeneralOptions().getOptions()) {
            builder.append(option.getDescriptionAndState()).append("\n");
        }
        String message = builder.toString();
        Dialogs.showMessageDialog(message);
    }

}
