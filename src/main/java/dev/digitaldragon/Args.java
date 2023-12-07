package dev.digitaldragon;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Args {
    @Parameter
    private String urlList;
    @Parameter(names = {"--output", "-o"})
    private String outputFile;

}