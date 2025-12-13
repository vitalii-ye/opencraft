package opencraft;

import javax.swing.*;
import java.awt.*;

public class RoundedButton extends JButton {
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("this-escape")
  public RoundedButton(String text) {
    super(text);
    setContentAreaFilled(false);
    setFocusPainted(false);
    setBorderPainted(false);
    setOpaque(false);
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
    
    if (getModel().isPressed()) {
      g2.setColor(getBackground().darker());
    } else if (getModel().isRollover()) {
      g2.setColor(getBackground().brighter());
    } else {
      g2.setColor(getBackground());
    }
    
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
    g2.dispose();
    super.paintComponent(g);
  }

  @Override
  protected void paintBorder(Graphics g) {
    // Do not paint border
  }
}
