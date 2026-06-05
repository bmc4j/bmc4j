package example.jakarta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An ordinary validated model. Its annotations become proof preconditions. */
public class User {

    @Min(0)
    @Max(120)
    public int age;

    @NotNull
    @Size(min = 3, max = 20)
    public String name;
}
