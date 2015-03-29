package de.uni_heidelberg.cos.aglohmann.greengateplugin;

/**
 * Created by matyas on 12/02/15.
 */
public enum Enzyme {
    ECO31I ("Eco31I", "GGTCTC", "GAGACC", "GGTCTC(1/5)",7, 4);

    Enzyme(String name, String recognitionSite, String revRecSite, String recPattern, int cutSite, int overhang) {
        this.name = name;
        this.recognitionSite = recognitionSite.toUpperCase();
        this.revRecSite = revRecSite.toUpperCase();
        this.recognitionPattern = recPattern;
        this.cutSite = cutSite;
        this.overhang = overhang;
    }

    public String dispName() {return name;}

    public String recognitionSite() {return recognitionSite;}

    public String revRecSite() {return revRecSite;}

    public String recognitionPattern() {return recognitionPattern;}

    public int cutSite() {return cutSite;}

    public int overhang() {return overhang;}

    private String name; //Name of the enzyme for display
    //All sequence information will be stored in uppercase.
    private String recognitionSite; //The recognition site for the enzyme (the exact nucleotide sequence)
    private String revRecSite; //The reverse of the recognition site for the enzyme
    private int cutSite; //The nucleotide number AFTER which the enzyme cuts, starting at the first nucleotide. '0' if it cuts at the beginning of the recognition sequence. So cutSite gives the last nucleotide still retained in the sequence.
    private int overhang; //The size of the overhang left by the enzyme. '0' means a blunt cut. Negative numbers mean 3' overhangs.
    private String recognitionPattern;
}
