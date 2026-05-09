#!/usr/bin/env python3
"""
Coverage report generator.
Usage: python3 generate_coverage_report.py

Requires: JaCoCo XML report at target/site/jacoco/jacoco.xml
Generates: docs/coverage-report-summary.md (commit-friendly summary)
"""
import xml.etree.ElementTree as ET
import os
import sys
from datetime import datetime

JACOCO_XML = "target/site/jacoco/jacoco.xml"
SUMMARY_MD = "docs/coverage-report-summary.md"

def generate():
    if not os.path.exists(JACOCO_XML):
        print(f"Error: {JACOCO_XML} not found. Run: mvn test jacoco:report")
        sys.exit(1)

    tree = ET.parse(JACOCO_XML)
    root = tree.getroot()

    metrics = {}
    for counter in root.findall('./counter'):
        ctype = counter.get('type')
        missed = int(counter.get('missed'))
        covered = int(counter.get('covered'))
        total = missed + covered
        pct = covered / total * 100 if total > 0 else 0
        metrics[ctype] = (covered, total, pct)

    # Top uncovered business classes
    classes = []
    for pkg in root.findall('./package'):
        pkgname = pkg.get('name', '')
        for child in pkg.findall('.//class'):
            cname = child.get('name', '')
            full = f'{pkgname}.{cname}'
            for counter in child.findall('./counter'):
                if counter.get('type') == 'INSTRUCTION':
                    missed = int(counter.get('missed'))
                    covered = int(counter.get('covered'))
                    total = missed + covered
                    pct = covered / total * 100 if total > 0 else 0
                    if total > 30 and missed > 30:
                        classes.append((missed, covered, total, pct, full))

    classes.sort(key=lambda x: -x[0])

    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    md = f"""# NovelTrans Test Coverage Report

> Generated: {now}

## Overall Metrics

| Metric | Covered | Total | Coverage |
|--------|---------|-------|----------|
| **Instruction** | {metrics.get('INSTRUCTION', (0,0,0))[0]:,} | {metrics.get('INSTRUCTION', (0,0,0))[1]:,} | **{metrics.get('INSTRUCTION', (0,0,0))[2]:.1f}%** |
| Branch | {metrics.get('BRANCH', (0,0,0))[0]:,} | {metrics.get('BRANCH', (0,0,0))[1]:,} | {metrics.get('BRANCH', (0,0,0))[2]:.1f}% |
| Line | {metrics.get('LINE', (0,0,0))[0]:,} | {metrics.get('LINE', (0,0,0))[1]:,} | {metrics.get('LINE', (0,0,0))[2]:.1f}% |
| Complexity | {metrics.get('COMPLEXITY', (0,0,0))[0]:,} | {metrics.get('COMPLEXITY', (0,0,0))[1]:,} | {metrics.get('COMPLEXITY', (0,0,0))[2]:.1f}% |
| Method | {metrics.get('METHOD', (0,0,0))[0]:,} | {metrics.get('METHOD', (0,0,0))[1]:,} | {metrics.get('METHOD', (0,0,0))[2]:.1f}% |
| Class | {metrics.get('CLASS', (0,0,0))[0]:,} | {metrics.get('CLASS', (0,0,0))[1]:,} | {metrics.get('CLASS', (0,0,0))[2]:.1f}% |

## Top 15 Uncovered Business Classes

| Missed | Total | Coverage | Class |
|--------|-------|----------|-------|
"""

    for missed, covered, total, pct, name in classes[:15]:
        short = name.split('.')[-1]
        md += f"| {missed} | {total} | {pct:.1f}% | `{short}` |\n"

    md += f"""
## Excluded Framework Code

The following code is excluded from coverage (infrastructure/mappers/config, no business logic):

- `entity/**` — Data entities (Lombok getters/setters)
- `dto/**` — Request/Response objects
- `domain/model/**` — Domain models (field-only)
- `adapter/out/persistence/converter/**` — Entity ↔ Model converters
- `adapter/out/persistence/*RepositoryAdapter` — MyBatis-Plus CRUD delegation
- `adapter/out/redis/Redis*Adapter`, `Redis*Service` — Pure Redis operation wrappers
- `adapter/in/security/**` — Spring Security filters
- `adapter/in/rest/GlobalExceptionHandler` — Framework exception handling
- `adapter/in/rest/web/Web*Controller` — Thin routing layer
- `adapter/out/stripe/**`, `adapter/out/embedding/**` — External SDK wrappers
- `adapter/out/email/**` — Email sending wrapper
- `domain/event/**` — Domain event data carriers
- `config/**`, `mapper/**`, `enums/**`, `properties/**`, `bootstrap/**`

## Detailed Report

Full interactive HTML report: [JaCoCo Coverage Report](coverage/index.html)
"""

    os.makedirs(os.path.dirname(SUMMARY_MD), exist_ok=True)
    with open(SUMMARY_MD, 'w') as f:
        f.write(md)

    instr_pct = metrics.get('INSTRUCTION', (0, 0, 0))[2]
    print(f"Coverage report generated: {SUMMARY_MD}")
    print(f"Instruction Coverage: {instr_pct:.1f}%")
    if instr_pct >= 80:
        print("Status: PASS (>= 80%)")
    else:
        print("Status: BELOW TARGET (< 80%)")

if __name__ == "__main__":
    generate()
