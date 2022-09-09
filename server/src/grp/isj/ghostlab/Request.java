package grp.isj.ghostlab;

public enum Request {
    GAMES, OGAME, NEWPL, REGIS, REGOK, REGNO, START,
    UNREG, UNROK, DUNNO, SIZEQ, SIZEA, LISTQ, LISTA, 
    PLAYR, GAMEQ, WELCO, POSIT, UPMOV, DOMOV, LEMOV,
    RIMOV, MOVEA, MOVEF, IQUIT, GOBYE, GLISQ, GLISA,
    GPLYR, MALLQ, MALLA, SENDQ, SENDA, NSEND, HELPQ,
    PSIZE, POWNT, PGHST, PWDTH, PHGHT;

    public static Request fromString(String req) {
        Request command = DUNNO;
        for (Request c : Request.values()) {
            if (c.toString().equals(req)) {
                command = c;
            }
        }
        return command;
    }
}
