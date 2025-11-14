import axios from 'axios'

export const importApi = {
  async upload(type: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    const res = await axios.post(`/api/v1/import/${type}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
  },
  async downloadTemplate(type: string, format: 'csv' | 'xls' | 'xlsx') {
    const res = await axios.get(`/api/v1/import/templates/${type}?format=${format}`, {
      responseType: 'blob',
    })
    return res.data as Blob
  },
  async downloadErrorReport(reportId: string) {
    const res = await axios.get(`/api/v1/import/error-reports/${reportId}`, {
      responseType: 'blob',
    })
    return res.data as Blob
  },
}
