package org.cirdles.topsoil.plot;

import org.cirdles.topsoil.symbols.SimpleSymbolMap;

import java.io.Serializable;
import java.util.Map;

public class PlotOptions extends SimpleSymbolMap<PlotOption<?>> implements Serializable {

    private static final long serialVersionUID = 9L;

    public PlotOptions() {
        super();
    }

    public PlotOptions(Map<PlotOption<?>, Object> map) {
        super(map);
    }

    public static PlotOptions defaultOptions() {
        PlotOptions options = new PlotOptions();
        options.putDefaultValues(PlotOption.ALL.toArray(new PlotOption[]{}));
        return options;
    }

}
