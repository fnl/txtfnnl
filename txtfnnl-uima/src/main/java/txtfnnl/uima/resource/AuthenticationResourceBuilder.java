package txtfnnl.uima.resource;

public interface AuthenticationResourceBuilder {
  /** Set the <code>username</code> for authentication. */
  public AuthenticationResourceBuilder setUsername(String username);

  /** Set the <code>password</code> for authentication. */
  public AuthenticationResourceBuilder setPassword(String password);
}
