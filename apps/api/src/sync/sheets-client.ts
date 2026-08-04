/**
 * Read-Only Google Sheets Integration Client
 * Structurally enforcing PRD §4.3 read-only boundary
 */
export class SheetsClient {
  private serviceAccountEmail: string;

  constructor(serviceAccountEmail: string = 'sync@openattend.iam.gserviceaccount.com') {
    this.serviceAccountEmail = serviceAccountEmail;
  }

  /**
   * Fetch spreadsheet metadata & tabs
   * Enforces scope: https://www.googleapis.com/auth/spreadsheets.readonly
   */
  async getSpreadsheetMetadata(sheetId: string) {
    if (!sheetId) throw new Error('SHEET_ID_REQUIRED');
    // Simulated Google Sheets API metadata response
    return {
      sheetId,
      title: 'VESIT Class Attendance 2026',
      sheets: [
        { properties: { title: 'Student List', gridProperties: { rowCount: 250, columnCount: 10 } } },
        { properties: { title: 'CS401', gridProperties: { rowCount: 100, columnCount: 6 } } },
        { properties: { title: 'CS402', gridProperties: { rowCount: 550, columnCount: 6 } } },
        { properties: { title: 'CS403', gridProperties: { rowCount: 100, columnCount: 6 } } },
        { properties: { title: 'Defaulters', gridProperties: { rowCount: 50, columnCount: 4 } } }
      ]
    };
  }

  /**
   * Fetch 2D value array from range
   */
  async getRangeValues(sheetId: string, range: string): Promise<string[][]> {
    if (!sheetId || !range) throw new Error('INVALID_RANGE_REQUEST');
    
    // Sample read-only spreadsheet row payload
    return [
      ['Date', 'Roll No', 'Status', 'Faculty', 'Remarks'],
      ['2026-08-01', '2024CS01', 'Present', 'Dr. Rao', 'Lecture 1'],
      ['2026-08-01', '2024CS02', 'Absent', 'Dr. Rao', 'Unexcused'],
      ['2026-08-01', '2024CS03', 'Present', 'Dr. Rao', 'Lecture 1'],
      ['2026-08-02', '2024CS01', 'Present', 'Prof. Patil', 'Lab Session'],
      ['2026-08-02', '2024CS02', 'Present', 'Prof. Patil', 'Lab Session']
    ];
  }
}
