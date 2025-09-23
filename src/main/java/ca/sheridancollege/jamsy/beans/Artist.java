
package ca.sheridancollege.jamsy.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Artist {
    private String name = "";
    private String imageUrl = "";
    private String previewUrl = "";
    private List<String> genres = new ArrayList<>();
}
