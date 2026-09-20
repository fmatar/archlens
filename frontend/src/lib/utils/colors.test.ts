import { describe, it, expect } from 'vitest';
import { getHealthColor, formatCrap, formatCoverage } from './colors';

describe('colors utility', () => {
  it('should compute health color correctly', () => {
    // Low CRAP (< 5), High Mutation (> 0.8) -> Green
    expect(getHealthColor(2.0, 0.9)).toBe('#22c55e');

    // Medium CRAP, Medium Mutation -> Amber
    expect(getHealthColor(10.0, 0.5)).toBe('#eab308');

    // High CRAP, Low Mutation -> Red
    expect(getHealthColor(20.0, 0.1)).toBe('#ef4444');
  });

  it('should format CRAP score correctly', () => {
    const formatted = formatCrap(4.25, 12.89, 1.44);
    expect(formatted).toBe('Crap μ 4.3  max 12.9  σ 1.4');
  });

  it('should format coverage percentage correctly', () => {
    expect(formatCoverage(0.854)).toBe('85%');
    expect(formatCoverage(1.0)).toBe('100%');
    expect(formatCoverage(0.0)).toBe('0%');
  });
});
