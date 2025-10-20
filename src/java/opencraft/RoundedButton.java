package opencraft;

import javax.swing.*;
import java.awt.*;

public class RoundedButton extends JButton {
  private static final long serialVersionUID = 1L;

  public RoundedButton(String text) {
    super(text);
  }
  
  // Override to ensure custom painting
  @Override
  public boolean isContentAreaFilled() {
    return false;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(getBackground());
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
    g2.dispose();
    super.paintComponent(g);
  }

  @Override
  protected void paintBorder(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(getForeground());
    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
    g2.dispose();
  }
}
